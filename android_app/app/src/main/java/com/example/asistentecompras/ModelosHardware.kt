package com.example.asistentecompras

import kotlinx.serialization.Serializable

/**
 * Modelo de datos serializable que representa un equipo del catálogo local,
 * incluyendo sus metadatos estructurados y su vector denso de 768 dimensiones.
 */
@Serializable
data class EquipoHardware(
    val id: String,
    val nombre: String,
    val tags: List<String>,
    val precio_mxn: Int,
    val texto: String,
    val embedding: List<Float>
)