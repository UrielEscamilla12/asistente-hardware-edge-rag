cmake_minimum_required(VERSION 3.22.1)
project("llama-android")

# Deshabilitar módulos auxiliares de llama.cpp para optimizar el tiempo de compilación
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Desactivar pruebas unitarias" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Desactivar ejemplos" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Desactivar servidor HTTP" FORCE)

# Integrar el motor de inferencia llama.cpp
add_subdirectory(llama_cpp)

# Definir la biblioteca nativa puente (JNI) para Kotlin
add_library(
        llama-android
        SHARED
        native-lib.cpp
)

# Localizar la biblioteca de registros nativos de Android (Logcat)
find_library(
        log-lib
        log
)

# Enlazar la biblioteca JNI con llama.cpp y Android Log
target_link_libraries(
        llama-android
        llama
        ${log-lib}
)