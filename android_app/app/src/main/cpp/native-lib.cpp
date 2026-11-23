#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama_cpp/include/llama.h"

#define LOG_TAG "MotorLlama_CPP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * Encapsula los punteros nativos requeridos para mantener activa la sesión
 * de inferencia local en memoria RAM.
 */
struct LlamaEstado {
    llama_model* model;
    llama_context* ctx;
    llama_sampler* sampler;
};

/**
 * Inicializa el backend de llama.cpp, carga el modelo cuantizado GGUF
 * y configura el contexto de inferencia multihilo con muestreo greedy.
 */
extern "C" JNIEXPORT jlong JNICALL
Java_com_example_asistentecompras_MotorGenerativoSLM_inicializarLlama(JNIEnv* env, jobject, jstring rutaModelo) {
    const char *ruta = env->GetStringUTFChars(rutaModelo, nullptr);
    LOGI("Inicializando backend nativo llama.cpp...");

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    llama_model* model = llama_load_model_from_file(ruta, mparams);

    if (!model) {
        LOGE("Error: No se pudo cargar el archivo GGUF desde la ruta proporcionada.");
        env->ReleaseStringUTFChars(rutaModelo, ruta);
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 2048;
    cparams.n_batch = 512;
    cparams.n_threads = 6;        // Hilos destinados a la generación autoregresiva
    cparams.n_threads_batch = 6;  // Hilos destinados a la evaluación del prompt por lotes

    llama_context* ctx = llama_new_context_with_model(model, cparams);

    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    LlamaEstado* estado = new LlamaEstado{model, ctx, sampler};

    env->ReleaseStringUTFChars(rutaModelo, ruta);
    LOGI("Modelo GGUF y contexto inicializados correctamente.");
    return reinterpret_cast<jlong>(estado);
}

/**
 * Tokeniza el prompt recibido desde Kotlin, evalúa el contexto en bloques
 * controlados y genera la respuesta técnica hasta alcanzar el token EOG o el límite configurado.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_asistentecompras_MotorGenerativoSLM_generarTextoLlama(JNIEnv* env, jobject, jlong punteroContexto, jstring promptStr) {
    LlamaEstado* estado = reinterpret_cast<LlamaEstado*>(punteroContexto);
    const char *prompt = env->GetStringUTFChars(promptStr, nullptr);

    std::string resultado = "";
    const llama_vocab * vocab = llama_model_get_vocab(estado->model);

    // Tokenización dinámica del prompt de entrada
    std::vector<llama_token> tokens_list(prompt ? strlen(prompt) + 2 : 2);
    int n_tokens = llama_tokenize(vocab, prompt, strlen(prompt), tokens_list.data(), tokens_list.size(), true, true);
    if (n_tokens < 0) {
        tokens_list.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, prompt, strlen(prompt), tokens_list.data(), tokens_list.size(), true, true);
    }
    tokens_list.resize(n_tokens);

    LOGI("Prompt tokenizado (%d tokens). Iniciando evaluación por bloques...", n_tokens);

    llama_batch batch = llama_batch_init(512, 0, 1);

    // Evaluación del prompt en bloques de 128 tokens para mantener estable el consumo de memoria
    int tamano_bloque = 128;
    for (int i = 0; i < n_tokens; i += tamano_bloque) {
        int n_eval = n_tokens - i;
        if (n_eval > tamano_bloque) n_eval = tamano_bloque;

        batch.n_tokens = n_eval;
        for (int j = 0; j < n_eval; j++) {
            batch.token[j] = tokens_list[i + j];
            batch.pos[j] = i + j;
            batch.n_seq_id[j] = 1;
            batch.seq_id[j][0] = 0;
            batch.logits[j] = false;
        }
        // Habilitar cálculo de logits únicamente en el último token del prompt
        if (i + n_eval == n_tokens) {
            batch.logits[n_eval - 1] = true;
        }

        if (llama_decode(estado->ctx, batch) != 0) {
            LOGE("Error al decodificar el bloque de entrada en llama_decode.");
            llama_batch_free(batch);
            env->ReleaseStringUTFChars(promptStr, prompt);
            return env->NewStringUTF("Error al procesar las especificaciones del catálogo.");
        }
    }

    // Ciclo de generación autoregresiva
    int n_curr = n_tokens;
    int max_tokens = 250;

    for (int i = 0; i < max_tokens; i++) {
        llama_token id = llama_sampler_sample(estado->sampler, estado->ctx, -1);
        llama_sampler_accept(estado->sampler, id);

        if (llama_token_is_eog(vocab, id)) {
            break;
        }

        char buf[128];
        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n >= 0) {
            resultado += std::string(buf, n);
        }

        batch.n_tokens = 1;
        batch.token[0] = id;
        batch.pos[0] = n_curr;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;

        if (llama_decode(estado->ctx, batch) != 0) {
            LOGE("Error durante la generación autoregresiva en el token %d.", i);
            break;
        }
        n_curr += 1;
    }

    llama_batch_free(batch);
    env->ReleaseStringUTFChars(promptStr, prompt);
    LOGI("Inferencia completada correctamente.");

    return env->NewStringUTF(resultado.c_str());
}

/**
 * Libera los recursos nativos (sampler, contexto, modelo y backend) al cerrar la aplicación.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_asistentecompras_MotorGenerativoSLM_liberarLlama(JNIEnv* env, jobject, jlong punteroContexto) {
    LlamaEstado* estado = reinterpret_cast<LlamaEstado*>(punteroContexto);
    if (estado) {
        if (estado->sampler) llama_sampler_free(estado->sampler);
        if (estado->ctx) llama_free(estado->ctx);
        if (estado->model) llama_free_model(estado->model);
        delete estado;
    }
    llama_backend_free();
    LOGI("Recursos nativos de llama.cpp liberados.");
}