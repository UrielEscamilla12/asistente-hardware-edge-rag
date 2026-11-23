package com.example.asistentecompras

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Repositorio local encargado de cargar y deserializar el catálogo de hardware
 * empaquetado en la carpeta assets de la aplicación.
 */
class RepositorioHardware(private val context: Context) {

    private val formateadorJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Lee catalogo_movil.json desde assets y lo convierte en una lista en memoria de EquipoHardware.
     * Retorna una lista vacía en caso de error de lectura o deserialización.
     */
    fun cargarCatalogoDesdeAssets(): List<EquipoHardware> {
        return try {
            val inputStream = context.assets.open("catalogo_movil.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val catalogo = formateadorJson.decodeFromString<List<EquipoHardware>>(jsonString)

            println("Catálogo cargado en memoria RAM. Total de equipos: ${catalogo.size}")
            catalogo
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error al cargar o deserializar catalogo_movil.json: ${e.message}")
            emptyList()
        }
    }
}