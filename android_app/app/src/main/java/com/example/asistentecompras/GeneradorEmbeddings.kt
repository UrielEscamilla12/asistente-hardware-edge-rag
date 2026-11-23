package com.example.asistentecompras

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.LongBuffer

/**
 * Motor de inferencia local basado en ONNX Runtime para generar embeddings densos
 * de 768 dimensiones a partir de consultas en lenguaje natural.
 */
class GeneradorEmbeddings(private val context: Context) {

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private val vocabulario = mutableMapOf<String, Long>()

    init {
        inicializarModelo()
    }

    /**
     * Carga el vocabulario desde tokenizer.json, copia el binario ONNX al almacenamiento
     * interno de la aplicación e inicializa la sesión de inferencia.
     */
    private fun inicializarModelo() {
        try {
            println("Cargando vocabulario del tokenizador...")
            val jsonString = context.assets.open("embeddinggemma_onnx/tokenizer.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val vocabJson = jsonObject.getJSONObject("model").getJSONObject("vocab")

            vocabJson.keys().forEach { key ->
                vocabulario[key] = vocabJson.getLong(key)
            }
            println("Vocabulario cargado: ${vocabulario.size} tokens.")

            val archivoDestino = File(context.filesDir, "model.onnx")
            if (!archivoDestino.exists()) {
                context.assets.open("embeddinggemma_onnx/model.onnx").use { input ->
                    FileOutputStream(archivoDestino).use { output -> input.copyTo(output) }
                }
            }

            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }
            session = environment.createSession(archivoDestino.absolutePath, options)
            println("Sesión de ONNX Runtime inicializada correctamente.")

        } catch (e: Exception) {
            e.printStackTrace()
            println("Error al inicializar ONNX Runtime: ${e.message}")
        }
    }

    /**
     * Tokeniza la consulta, ejecuta la inferencia en ONNX Runtime y aplica
     * mean pooling sobre el tensor de salida para obtener el vector de consulta.
     */
    fun generarEmbedding(textoConsulta: String): List<Float> {
        val sessionActual = session ?: return List(768) { 0.0f }
        var embeddingFinal = List(768) { 0.0f }

        try {
            // Normalización de texto y mapeo a identificadores del vocabulario SentencePiece
            val textoLimpio = textoConsulta.lowercase()
                .replace(Regex("[^a-záéíóúüñ0-9\\s]"), " ")
                .trim()
            val palabras = textoLimpio.split(Regex("\\s+")).filter { it.isNotEmpty() }

            val ids = mutableListOf<Long>()
            val bosId = vocabulario["<bos>"] ?: 2L
            val unkId = vocabulario["<unk>"] ?: 0L
            val spChar = "\u2581" // Prefijo de inicio de palabra en SentencePiece

            ids.add(bosId)
            for (palabra in palabras) {
                val id = vocabulario["$spChar$palabra"] ?: vocabulario[palabra] ?: unkId
                ids.add(id)
            }

            val mask = LongArray(ids.size) { 1L }

            // Construcción de tensores de entrada (input_ids y attention_mask)
            val shape = longArrayOf(1, ids.size.toLong())
            val tensorIds = OnnxTensor.createTensor(environment, LongBuffer.wrap(ids.toLongArray()), shape)
            val tensorMask = OnnxTensor.createTensor(environment, LongBuffer.wrap(mask), shape)

            val inputs = mapOf("input_ids" to tensorIds, "attention_mask" to tensorMask)
            val resultados = sessionActual.run(inputs)

            // Mean pooling sobre la secuencia de estados ocultos devueltos por el modelo
            val iterador = resultados.iterator()
            if (iterador.hasNext()) {
                val onnxTensor = iterador.next().value as OnnxTensor
                val dimensiones = onnxTensor.info.shape
                val floatBuffer = onnxTensor.floatBuffer

                if (dimensiones.size == 3) {
                    val seqLen = dimensiones[1].toInt()
                    val tamanoVector = dimensiones[2].toInt()
                    val suma = FloatArray(tamanoVector)
                    val tokenActual = FloatArray(tamanoVector)

                    for (t in 0 until seqLen) {
                        floatBuffer.get(tokenActual)
                        for (i in 0 until tamanoVector) {
                            suma[i] += tokenActual[i]
                        }
                    }
                    embeddingFinal = suma.map { it / seqLen.toFloat() }
                } else {
                    val tamanoVector = dimensiones.last().toInt()
                    val arraySimple = FloatArray(tamanoVector)
                    floatBuffer.get(arraySimple)
                    embeddingFinal = arraySimple.toList()
                }
                println("Embedding generado. Tokens: ${ids.size} | Dimensión: ${embeddingFinal.size}")
            }

            tensorIds.close()
            tensorMask.close()
            resultados.close()

        } catch (e: Exception) {
            e.printStackTrace()
            println("Error durante la generación del embedding: ${e.message}")
        }

        return embeddingFinal
    }
}