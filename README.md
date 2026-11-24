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
