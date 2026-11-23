package com.example.asistentecompras

/**
 * Representa las restricciones simbólicas extraídas de la consulta del usuario.
 */
data class FiltrosExtraidos(
    val presupuestoMinimo: Int? = null,
    val presupuestoMaximo: Int? = null,
    val tags: List<String> = emptyList()
)

/**
 * Extractor simbólico de restricciones de presupuesto y etiquetas categóricas
 * mediante coincidencia léxica y expresiones regulares.
 */
object FiltroHibrido {

    /**
     * Analiza una consulta en lenguaje natural para extraer rangos de presupuesto
     * (mínimo y/o máximo en MXN) y etiquetas de formato o caso de uso.
     */
    fun extraerFiltros(consulta: String): FiltrosExtraidos {
        val texto = consulta.lowercase()
        var presupuestoMinimo: Int? = null
        var presupuestoMaximo: Int? = null
        val tagsBuscados = mutableListOf<String>()

        // Identificación de factor de forma
        if (texto.contains("laptop") || texto.contains("portátil") || texto.contains("portatil")) {
            tagsBuscados.add("laptop")
        }
        if (texto.contains("escritorio") || texto.contains("desktop") || Regex("""\bpc\b""").containsMatchIn(texto)) {
            tagsBuscados.add("desktop")
        }
        if (texto.contains("tablet") || texto.contains("híbrido") || texto.contains("hibrido") ||
            texto.contains("híbrida") || texto.contains("hibrida") || texto.contains("2-en-1")
        ) {
            tagsBuscados.add("tablet")
        }

        // Identificación de perfil de uso
        if (texto.contains("jugar") || texto.contains("videojuegos") || texto.contains("gamer")) {
            tagsBuscados.add("gaming")
        }
        if (texto.contains("edición") || texto.contains("edicion") || texto.contains("fotos") ||
            texto.contains("diseño") || texto.contains("creador")
        ) {
            tagsBuscados.add("creador")
        }
        if (texto.contains("viajo") || texto.contains("batería") || texto.contains("bateria") ||
            texto.contains("movilidad") || texto.contains("ligera") || texto.contains("mochila")
        ) {
            tagsBuscados.add("movilidad")
        }

        // Sanitización para evitar falsos positivos con modelos de hardware o resoluciones (ej. RTX 4060, 16GB, 4K)
        val textoParaPresupuesto = texto
            .replace(Regex("""\b(?:rtx|gtx|rx|mx|ryzen|core|i[3579])(?:\s+\d)?\s*\d{3,4}[a-z]*\b"""), " ")
            .replace(Regex("""\b\d+\s*(?:gb|tb|mb|hz|mhz|ghz|p|w|mah)\b"""), " ")
            .replace(Regex("""\b[248]k\b"""), " ")

        // Expresiones regulares para montos ("25 mil", "15.5 mil", "20k", "25,000", "25000") y dirección del límite
        val regexMonto = Regex("""\b(\d+(?:[.,]\d+)?)\s*(mil|k)\b|\b(\d{1,3}(?:,\d{3})+|\d{4,6})\b""")
        val regexMinPrevio = Regex("""(a partir de|desde|m[ií]nimo|arriba de|m[aá]s de|mayor a|superior a)(?:\s+(?:los|unos|unas))?\s*\$?\s*$""")
        val regexMinPosterior = Regex("""^\s*(?:pesos|mxn)?\s*(?:para arriba|o m[aá]s|en adelante)""")

        val montosEncontrados = mutableListOf<Pair<Int, MatchResult>>()
        for (match in regexMonto.findAll(textoParaPresupuesto)) {
            val monto = if (match.groupValues[1].isNotEmpty()) {
                val base = match.groupValues[1].replace(",", ".").toDoubleOrNull()
                base?.times(1000)?.toInt()
            } else {
                match.groupValues[3].replace(",", "").toIntOrNull()
            }

            if (monto != null && monto > 1000) {
                montosEncontrados.add(Pair(monto, match))
            }
        }

        if (montosEncontrados.size >= 2) {
            // Interpretación de rango explícito (ej. "entre 15 mil y 25 mil")
            val m1 = montosEncontrados[0].first
            val m2 = montosEncontrados[1].first
            presupuestoMinimo = minOf(m1, m2)
            presupuestoMaximo = maxOf(m1, m2)
        } else if (montosEncontrados.size == 1) {
            val (monto, match) = montosEncontrados[0]
            val inicioVentana = maxOf(0, match.range.first - 25)
            val finVentana = minOf(textoParaPresupuesto.length, match.range.last + 20)
            val contextoPrevio = textoParaPresupuesto.substring(inicioVentana, match.range.first)
            val contextoPosterior = textoParaPresupuesto.substring(match.range.last + 1, finVentana)

            if (regexMinPrevio.containsMatchIn(contextoPrevio) || regexMinPosterior.containsMatchIn(contextoPosterior)) {
                presupuestoMinimo = monto
            } else {
                presupuestoMaximo = monto
            }
        }

        // Heurística por defecto para consultas cualitativas de bajo presupuesto
        if (presupuestoMinimo == null && presupuestoMaximo == null && (
                    texto.contains("barata") || texto.contains("barato") ||
                            texto.contains("económica") || texto.contains("economica")
                    )
        ) {
            presupuestoMaximo = 15000
        }

        return FiltrosExtraidos(presupuestoMinimo, presupuestoMaximo, tagsBuscados)
    }
}