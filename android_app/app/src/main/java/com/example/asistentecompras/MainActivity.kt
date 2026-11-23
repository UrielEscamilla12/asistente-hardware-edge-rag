package com.example.asistentecompras

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val AzulMarino = Color(0xFF0D1B2A)
val Borgona = Color(0xFF800020)

/**
 * Interfaz conversacional principal construida con Jetpack Compose.
 * Orquesta el flujo completo RAG local: filtrado simbólico, generación de embeddings,
 * búsqueda vectorial, validación de dominio e inferencia generativa con Llama 3.2.
 */
@Composable
fun PantallaAsistente(
    catalogo: List<EquipoHardware>,
    motorVectorial: MotorVectorial,
    generadorEmbeddings: GeneradorEmbeddings,
    motorGenerativo: MotorGenerativoSLM
) {
    var consultaUsuario by remember { mutableStateOf("") }
    var procesando by remember { mutableStateOf(false) }

    val mensajesUI = remember { mutableStateListOf<MensajeChat>() }

    LaunchedEffect(Unit) {
        if (mensajesUI.isEmpty()) {
            mensajesUI.add(
                MensajeChat(
                    "Asistente",
                    "¡Hola! Soy tu asistente de compras para equipos de cómputo. Describe lo que buscas."
                )
            )
        }
    }

    val memoriaChat = remember { GestorHistorial(maxTurnos = 3) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().imePadding().statusBarsPadding()) {
        // Barra superior con título y acción de reinicio de sesión
        Surface(modifier = Modifier.fillMaxWidth(), color = AzulMarino, shadowElevation = 4.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "←",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Asistente Técnico",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                // Reinicio completo del historial visual y de la cola FIFO en memoria
                Text(
                    text = "🗑️",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickable {
                            mensajesUI.clear()
                            memoriaChat.limpiar()
                            mensajesUI.add(
                                MensajeChat(
                                    "Asistente",
                                    "¡Hola! Soy tu asistente de compras para equipos de cómputo. Describe lo que buscas."
                                )
                            )
                        }
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {

            // Historial conversacional persistente
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(mensajesUI) { mensaje ->
                    if (mensaje.rol == "Usuario") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Box(
                                modifier = Modifier
                                    .background(Borgona, shape = RoundedCornerShape(18.dp, 18.dp, 2.dp, 18.dp))
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = mensaje.contenido,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .border(
                                        width = 1.5.dp,
                                        color = Borgona,
                                        shape = RoundedCornerShape(2.dp, 16.dp, 16.dp, 16.dp)
                                    ),
                                color = Color.White,
                                shape = RoundedCornerShape(2.dp, 16.dp, 16.dp, 16.dp),
                                shadowElevation = 1.dp
                            ) {
                                Text(
                                    text = mensaje.contenido,
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.DarkGray
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (procesando) {
                CircularProgressIndicator(
                    color = Borgona,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Barra de entrada de consultas y botón de envío
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = consultaUsuario,
                    onValueChange = { consultaUsuario = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe aquí...") },
                    enabled = !procesando,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        disabledContainerColor = Color.LightGray,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val textoConsulta = consultaUsuario.trim()
                        consultaUsuario = ""
                        procesando = true

                        mensajesUI.add(MensajeChat("Usuario", textoConsulta))

                        coroutineScope.launch {
                            val resultadoFinal: String = withContext(Dispatchers.IO) {
                                // 1. Detección rápida de saludos simples sin disparar inferencia
                                val saludoRegex = Regex(
                                    "^(hola|buenos d[ií]as|buenas tardes|buenas noches|qu[eé] tal|hey|saludos)[!¡.,?¿\\s]*$",
                                    RegexOption.IGNORE_CASE
                                )
                                if (saludoRegex.matches(textoConsulta)) {
                                    return@withContext "¡Hola! Soy tu asistente de hardware. Dime qué tipo de equipo buscas (laptop, desktop o tablet), para qué lo usarás y tu presupuesto aproximado."
                                }

                                // 2. Extracción simbólica de filtros y vectorización de la consulta
                                val filtros = FiltroHibrido.extraerFiltros(textoConsulta)
                                val presupuestoMin = filtros.presupuestoMinimo
                                val presupuestoMax = filtros.presupuestoMaximo
                                val tags = filtros.tags
                                println("RAG_TESIS | Filtros -> Min: $presupuestoMin, Max: $presupuestoMax, Tags: $tags")
                                val vectorUsuario = generadorEmbeddings.generarEmbedding(textoConsulta)

                                // 3. Recuperación híbrida Top-K sobre el catálogo en RAM
                                val resultados = motorVectorial.buscarEquipos(
                                    consultaEmbedding = vectorUsuario,
                                    catalogo = catalogo,
                                    limiteResultados = 3,
                                    presupuestoMinimo = presupuestoMin,
                                    presupuestoMaximo = presupuestoMax,
                                    tagsRequeridos = tags
                                )

                                if (resultados.isEmpty()) {
                                    return@withContext "No encontré equipos que cumplan con esas restricciones básicas."
                                }

                                // 4. Umbral adaptativo de similitud coseno para descartar consultas fuera de dominio
                                val equipoTop = resultados.first()
                                val similitud = motorVectorial.calcularSimilitudCoseno(vectorUsuario, equipoTop.embedding)
                                val sinFiltros = tags.isEmpty() && presupuestoMin == null && presupuestoMax == null
                                val umbralDominio = if (sinFiltros) 0.70f else 0.58f
                                println("RAG_TESIS | Equipo Top: ${equipoTop.nombre} | Similitud: $similitud | Umbral: $umbralDominio")

                                if (similitud < umbralDominio) {
                                    return@withContext "Lo siento, soy un asistente especializado en hardware de cómputo. No puedo ayudarte con ese tema."
                                }

                                // 5. Construcción del prompt estructurado para Llama 3.2 Instruct
                                val especificacionesCortas = equipoTop.texto.substringBefore("Ventajas:").trim()

                                val promptFinal = """
                                    <|begin_of_text|><|start_header_id|>system<|end_header_id|>
                                    Eres un ingeniero experto recomendando hardware de cómputo.
                                    Tu única tarea es redactar UN SOLO párrafo breve (máximo 3 oraciones y menos de 60 palabras) justificando técnicamente por qué el [Equipo Seleccionado] cumple con la [Petición del Usuario].
                                    Reglas estrictas: no saludes, no uses viñetas ni listas, usa únicamente las especificaciones proporcionadas y termina siempre con punto final.
                                    <|eot_id|><|start_header_id|>user<|end_header_id|>
                                    [Equipo Seleccionado]
                                    Nombre: ${equipoTop.nombre}
                                    Especificaciones: $especificacionesCortas

                                    [Petición del Usuario]
                                    $textoConsulta<|eot_id|><|start_header_id|>assistant<|end_header_id|>
                                """.trimIndent()

                                // 6. Inferencia generativa local vía JNI
                                val rawJustificacion = motorGenerativo.generarRespuestaV3(promptFinal).trim()

                                // Validación de rechazo por parte del SLM para evitar respuestas mal estructuradas
                                val esRechazo = rawJustificacion.startsWith("Lo siento", ignoreCase = true) ||
                                        rawJustificacion.contains("no puedo cumplir", ignoreCase = true) ||
                                        rawJustificacion.contains("no puedo proporcionar", ignoreCase = true) ||
                                        rawJustificacion.contains("no puedo ayudar", ignoreCase = true)

                                if (esRechazo) {
                                    return@withContext "Lo siento, no logré identificar una solicitud clara de hardware de cómputo. ¿Puedes indicarme qué tipo de equipo buscas y tu presupuesto?"
                                }

                                // Saneamiento de cierre de oración en caso de truncamiento por límite de tokens
                                val justificacionPura = if (
                                    rawJustificacion.endsWith(".") ||
                                    rawJustificacion.endsWith("!") ||
                                    rawJustificacion.endsWith("?")
                                ) {
                                    rawJustificacion
                                } else {
                                    val ultimoPunto = rawJustificacion.lastIndexOf('.')
                                    if (ultimoPunto > 30) rawJustificacion.substring(0, ultimoPunto + 1) else rawJustificacion
                                }

                                return@withContext java.lang.StringBuilder().apply {
                                    append("**Recomendación:** ${equipoTop.nombre}\n\n")
                                    append("**Justificación:** $justificacionPura\n\n")
                                    append("**Categorías:** ${equipoTop.tags.joinToString(", ")}\n")
                                    append("**Precio de referencia:** $${equipoTop.precio_mxn} MXN")
                                }.toString()
                            }

                            memoriaChat.agregarMensaje("Usuario", textoConsulta)
                            memoriaChat.agregarMensaje("Asistente", resultadoFinal)
                            mensajesUI.add(MensajeChat("Asistente", resultadoFinal))

                            procesando = false
                        }
                    },
                    enabled = !procesando && consultaUsuario.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AzulMarino)
                ) {
                    Text("Enviar")
                }
            }
        }
    }
}

/**
 * Actividad de entrada que inicializa los motores locales de inferencia,
 * carga el catálogo de hardware en RAM y despliega la interfaz Compose.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val generadorEmbeddings = GeneradorEmbeddings(this)
        val motorVectorial = MotorVectorial()
        val motorGenerativo = MotorGenerativoSLM(this)

        val repositorio = RepositorioHardware(this)
        val catalogo = repositorio.cargarCatalogoDesdeAssets()

        setContent {
            MaterialTheme {
                PantallaAsistente(
                    catalogo = catalogo,
                    motorVectorial = motorVectorial,
                    generadorEmbeddings = generadorEmbeddings,
                    motorGenerativo = motorGenerativo
                )
            }
        }
    }
}