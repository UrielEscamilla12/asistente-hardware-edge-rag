# Asistente de Recomendación de Hardware con Edge AI y RAG Local

Repositorio oficial del proyecto de titulación desarrollado para la **Escuela Superior de Ingeniería Mecánica y Eléctrica (ESIME) — Instituto Politécnico Nacional (IPN)**.

**Autor:** Luis Uriel Escamilla Becerril

---

## Descripción General

Aplicación móvil para Android diseñada para operar **100 % fuera de línea (Edge AI)** mediante una arquitectura de **Generación Aumentada por Recuperación (RAG)** neuro-simbólica. El sistema recomienda equipos de cómputo a partir de un catálogo estructurado de 36 dispositivos, combinando filtrado simbólico estricto (presupuesto y categorías), búsqueda semántica en memoria RAM con embeddings densos de 768 dimensiones e inferencia generativa local mediante un modelo de lenguaje pequeño (SLM) cuantizado a 4 bits.

## Tecnologías Principales

* **Aplicación Móvil (`android_app/`):** Kotlin, Jetpack Compose, Kotlin Coroutines (`Dispatchers.IO` / `Dispatchers.Main`).
* **Motor de Embeddings Local:** ONNX Runtime Android (`google/embeddinggemma-300m`) con *Mean Pooling*.
* **Recuperación Híbrida:** Filtrado simbólico mediante expresiones regulares (`FiltroHibrido.kt`) y similitud coseno en RAM (`MotorVectorial.kt`).
* **Motor Generativo Local:** C++ / JNI / CMake 3.22.1 enlazado con `llama.cpp` ejecutando `Llama-3.2-1B-Instruct-Q4_K_M.gguf`.
* **Pipeline Offline (`python_offline/`):** Python, Hugging Face `transformers`, `optimum[onnxruntime]` y PyTorch.

---

## Estructura del Repositorio

```text
asistente-hardware-edge-rag/
├── python_offline/
│   ├── data/
│   │   └── processed/
│   │       ├── documentos.json
│   │       └── embeddings.npy
│   ├── models/
│   │   └── export/
│   │       ├── embeddinggemma_onnx/
│   │       └── catalogo_movil.json
│   ├── exportar_onnx.py
│   └── generar_catalogo_movil.py
└── android_app/
    ├── app/
    │   └── src/
    │       └── main/
    │           ├── assets/
    │           │   └── catalogo_movil.json
    │           ├── cpp/
    │           │   ├── CMakeLists.txt
    │           │   └── native-lib.cpp
    │           ├── java/com/example/asistentecompras/
    │           │   ├── FiltroHibrido.kt
    │           │   ├── GeneradorEmbeddings.kt
    │           │   ├── GestorHistorial.kt
    │           │   ├── MainActivity.kt
    │           │   ├── ModelosHardware.kt
    │           │   ├── MotorGenerativoSLM.kt
    │           │   ├── MotorVectorial.kt
    │           │   └── RepositorioHardware.kt
    │           └── AndroidManifest.xml
    ├── build.gradle.kts
    └── settings.gradle.kts
```

---

## Descarga de Modelos Binarios y Dependencias Externas

Debido a las restricciones de tamaño y volumen de archivos de GitHub, los modelos cuantizados, el tokenizador extendido y el código fuente de `llama.cpp` se alojan de forma externa en Google Drive:

* **Carpeta de binarios y dependencias:** [Enlace a Google Drive — PENDIENTE DE ACTUALIZAR]

### Ubicación exacta de los archivos descargados

1. **`Llama-3.2-1B-Instruct-Q4_K_M.gguf`**
   * Copiar en: `android_app/app/src/main/assets/Llama-3.2-1B-Instruct-Q4_K_M.gguf`
2. **`model.onnx` y `tokenizer.json`**
   * Copiar en: `android_app/app/src/main/assets/embeddinggemma_onnx/`
   * *(Opcional, para reejecutar `generar_catalogo_movil.py` en Python)*: copiar también en `python_offline/models/export/embeddinggemma_onnx/`.
3. **`llama_cpp.zip`**
   * Descomprimir dentro de `android_app/app/src/main/cpp/` de manera que quede la ruta: `android_app/app/src/main/cpp/llama_cpp/`.

---

## Instrucciones de Compilación (Android Studio)

1. Clonar o descargar este repositorio.
2. Descargar y colocar los 4 recursos externos de Google Drive en las rutas indicadas en la sección anterior.
3. Abrir la carpeta `android_app/` en **Android Studio** (requiere Android NDK y CMake 3.22.1 instalados desde SDK Manager).
4. Sincronizar Gradle (`Sync Project with Gradle Files`) y ejecutar en un dispositivo físico Android (ARM64 recomendado por el consumo de RAM del modelo GGUF).

## Ejecución del Pipeline Offline (Python)

Si se desea regenerar el modelo ONNX o recalcular el catálogo vectorizado:

```bash
cd python_offline
python exportar_onnx.py
python generar_catalogo_movil.py
```
