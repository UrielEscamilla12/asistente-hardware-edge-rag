package com.example.asistentecompras

import kotlin.math.sqrt

/**
 * Motor de recuperación híbrida que combina filtrado simbólico estricto
 * (presupuesto y etiquetas) con ordenamiento semántico por similitud coseno.
 */
class MotorVectorial {

    /**
     * Calcula la similitud coseno entre dos vectores de igual dimensión.
     */
    fun calcularSimilitudCoseno(vectorA: List<Float>, vectorB: List<Float>): Float {
        if (vectorA.size != vectorB.size) return 0f
        var productoPunto = 0f
        var normaA = 0f
        var normaB = 0f
        for (i in vectorA.indices) {
            productoPunto += vectorA[i] * vectorB[i]
            normaA += vectorA[i] * vectorA[i]
            normaB += vectorB[i] * vectorB[i]
        }
        return if (normaA == 0f || normaB == 0f) 0f else productoPunto / (sqrt(normaA) * sqrt(normaB))
    }

    /**
     * Filtra el catálogo según restricciones de presupuesto y etiquetas,
     * aplica relajación sobre etiquetas secundarias si no hay coincidencias exactas
     * y retorna los mejores candidatos ordenados por similitud coseno.
     */
    fun buscarEquipos(
        consultaEmbedding: List<Float>,
        catalogo: List<EquipoHardware>,
        limiteResultados: Int = 3,
        presupuestoMinimo: Int? = null,
        presupuestoMaximo: Int? = null,
        tagsRequeridos: List<String> = emptyList()
    ): List<EquipoHardware> {
        fun cumplePresupuesto(precio: Int): Boolean {
            val pasaMin = presupuestoMinimo == null || precio >= presupuestoMinimo
            val pasaMax = presupuestoMaximo == null || precio <= presupuestoMaximo
            return pasaMin && pasaMax
        }

        // Fase 1: Filtrado simbólico estricto (rango de presupuesto y totalidad de etiquetas)
        var candidatos = catalogo.filter { equipo ->
            val pasaTags = if (tagsRequeridos.isNotEmpty()) {
                tagsRequeridos.all { tagBuscado -> equipo.tags.contains(tagBuscado) }
            } else true
            cumplePresupuesto(equipo.precio_mxn) && pasaTags
        }

        // Fase 2: Respaldo estructural conservando únicamente etiquetas esenciales si la intersección estricta queda vacía
        if (candidatos.isEmpty() && tagsRequeridos.size > 1) {
            val tagsEsenciales = tagsRequeridos.filter {
                it in listOf("laptop", "desktop", "tablet", "hibrida", "gaming")
            }
            if (tagsEsenciales.isNotEmpty() && tagsEsenciales.size < tagsRequeridos.size) {
                candidatos = catalogo.filter { equipo ->
                    val pasaEsenciales = tagsEsenciales.all { tag -> equipo.tags.contains(tag) }
                    cumplePresupuesto(equipo.precio_mxn) && pasaEsenciales
                }
            }
        }

        // Fase 3: Ordenamiento semántico mediante similitud coseno y selección Top-K
        return candidatos.map { equipo ->
            val similitud = calcularSimilitudCoseno(consultaEmbedding, equipo.embedding)
            Pair(equipo, similitud)
        }
            .sortedByDescending { it.second }
            .take(limiteResultados)
            .map { it.first }
    }
}