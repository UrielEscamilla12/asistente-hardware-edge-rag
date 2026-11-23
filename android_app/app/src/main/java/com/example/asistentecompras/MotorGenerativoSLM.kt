package com.example.asistentecompras

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Gestiona el ciclo de vida e inferencia local del modelo cuantizado Llama 3.2 (GGUF)
 * mediante llamadas nativas JNI a la biblioteca C++ compilada con llama.cpp.
 */
class MotorGenerativoSLM(private val context: Context) {

    companion object {
        init {
            try {
                System.loadLibrary("llama-android")
                println("Biblioteca nativa llama-android cargada correctamente.")
            } catch (e: UnsatisfiedLinkError) {
                println("Error: No se pudo cargar la biblioteca nativa llama-android.")
            }
        }
    }

    // Declaración de funciones puente JNI implementadas en native-lib.cpp
    private external fun inicializarLlama(rutaModelo: String): Long
    private external fun generarTextoLlama(punteroContexto: Long, prompt: String): String
    private external fun liberarLlama(punteroContexto: Long)

    /**
     * Copia el archivo binario GGUF desde assets hacia el almacenamiento interno
     * de la aplicación si aún no existe, y retorna su ruta absoluta.
     */
    suspend fun prepararModeloGGUF(): String = withContext(Dispatchers.IO) {
        val nombreArchivo = "Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        val archivoDestino = File(context.filesDir, nombreArchivo)

        if (!archivoDestino.exists()) {
            println("Extrayendo modelo GGUF al almacenamiento interno...")
            context.assets.open(nombreArchivo).use { inputStream ->
                FileOutputStream(archivoDestino).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            println("Modelo GGUF disponible en almacenamiento interno.")
        }

        return@withContext archivoDestino.absolutePath
    }

    /**
     * Ejecuta la inferencia generativa local a partir de un prompt estructurado completo.
     */
    suspend fun generarRespuestaV3(promptCompleto: String): String = withContext(Dispatchers.IO) {
        val rutaModelo = prepararModeloGGUF()
        val respuestaGenerada = inferenciaJNIReal(promptCompleto, rutaModelo)
        return@withContext respuestaGenerada
    }

    /**
     * Variante de generación directa para un único equipo candidato (arquitectura V2),
     * conservada con fines de comparación de desempeño.
     */
    suspend fun generarRespuestaHibrida(consulta: String, equipoTop: EquipoHardware): String = withContext(Dispatchers.IO) {
        val promptSistema = "Tu única tarea es redactar un solo párrafo conciso de máximo 3 oraciones justificando técnicamente por qué las especificaciones del equipo dado cumplen con la consulta del usuario. Sé directo y profesional."
        val promptUsuario = "Consulta del usuario: $consulta\nEquipo: ${equipoTop.nombre}\nEspecificaciones: ${equipoTop.texto}\n\nRedacta la justificación técnica:"

        val promptCompleto = """
            <|begin_of_text|><|start_header_id|>system<|end_header_id|>
            $promptSistema<|eot_id|><|start_header_id|>user<|end_header_id|>
            $promptUsuario<|eot_id|><|start_header_id|>assistant<|end_header_id|>
        """.trimIndent()

        val rutaModelo = prepararModeloGGUF()
        val justificacionGenerada = inferenciaJNIReal(promptCompleto, rutaModelo)

        val respuestaFinal = StringBuilder().apply {
            append("**Recomendación:** ${equipoTop.nombre}\n\n")
            append("**Justificación:** $justificacionGenerada\n\n")
            append("**Tags:** ${equipoTop.tags.joinToString(", ")}\n")
            append("**Precio de referencia:** $${equipoTop.precio_mxn} MXN\n")
        }.toString()

        return@withContext respuestaFinal
    }

    /**
     * Inicializa el contexto nativo en C++, ejecuta la generación autoregresiva
     * y garantiza la liberación de memoria RAM en el bloque finally.
     */
    private suspend fun inferenciaJNIReal(prompt: String, rutaModelo: String): String = withContext(Dispatchers.IO) {
        println("Iniciando inferencia local con Llama 3.2...")
        var respuesta = ""
        var punteroLlama = 0L

        try {
            punteroLlama = inicializarLlama(rutaModelo)
            if (punteroLlama != 0L) {
                respuesta = generarTextoLlama(punteroLlama, prompt)
            } else {
                respuesta = "Error interno: No se pudo cargar el archivo GGUF en la capa nativa."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            respuesta = "Excepción durante la inferencia nativa: ${e.message}"
        } finally {
            if (punteroLlama != 0L) {
                liberarLlama(punteroLlama)
            }
        }
        return@withContext respuesta.trim()
    }
}