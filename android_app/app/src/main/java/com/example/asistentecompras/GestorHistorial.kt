package com.example.asistentecompras

/**
 * Representa una intervención individual dentro de la conversación.
 */
data class MensajeChat(val rol: String, val contenido: String)

/**
 * Administra la memoria conversacional mediante una cola FIFO acotada
 * por un número máximo de turnos (usuario + asistente).
 */
class GestorHistorial(private val maxTurnos: Int = 3) {

    private val historial = mutableListOf<MensajeChat>()

    /**
     * Registra un mensaje y descarta los más antiguos si se supera el límite de turnos.
     */
    fun agregarMensaje(rol: String, contenido: String) {
        historial.add(MensajeChat(rol, contenido))

        // Cada turno completo equivale a dos mensajes (consulta del usuario y respuesta del asistente)
        val maxMensajes = maxTurnos * 2
        while (historial.size > maxMensajes) {
            historial.removeAt(0)
        }
    }

    /**
     * Devuelve el historial acumulado en formato de texto plano para inyección en el prompt.
     */
    fun obtenerHistorialFormateado(): String {
        if (historial.isEmpty()) return "No hay historial previo."

        val constructor = StringBuilder()
        for (mensaje in historial) {
            constructor.append("${mensaje.rol}: ${mensaje.contenido}\n")
        }
        return constructor.toString()
    }

    /**
     * Vacía completamente el historial de la sesión actual.
     */
    fun limpiar() {
        historial.clear()
    }
}