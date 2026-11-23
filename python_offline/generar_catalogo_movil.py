"""Genera catalogo_movil.json con embeddings de 768 dimensiones alineados con ONNX."""

import json
from pathlib import Path
from optimum.onnxruntime import ORTModelForFeatureExtraction
import torch
from transformers import AutoTokenizer


RUTA_DOCS = Path("../data/processed/documentos.json")
RUTA_ONNX = Path("../models/export/embeddinggemma_onnx")
RUTA_SALIDA = Path("../models/export/catalogo_movil.json")
MAX_LENGTH = 512


def generar_embedding_onnx(
    texto: str,
    tokenizer: AutoTokenizer,
    ort_model: ORTModelForFeatureExtraction,
) -> list[float]:
    """Calcula el embedding normalizado mediante mean pooling sobre la salida ONNX."""
    inputs = tokenizer(
        texto.lower(),
        return_tensors="pt",
        truncation=True,
        max_length=MAX_LENGTH,
    )
    with torch.no_grad():
        outputs = ort_model(**inputs)

    token_embeddings = outputs.last_hidden_state
    mask = inputs["attention_mask"].unsqueeze(-1).expand(token_embeddings.size()).float()
    vector = (token_embeddings * mask).sum(dim=1) / mask.sum(dim=1).clamp(min=1e-9)
    return vector[0].cpu().numpy().tolist()


def construir_catalogo_movil(
    ruta_docs: Path = RUTA_DOCS,
    ruta_onnx: Path = RUTA_ONNX,
    ruta_salida: Path = RUTA_SALIDA,
) -> None:
    """Carga el catálogo procesado, calcula los vectores y exporta el JSON móvil."""
    with ruta_docs.open("r", encoding="utf-8") as f:
        documentos = json.load(f)
    print(f"Documentos cargados: {len(documentos)}")

    tokenizer = AutoTokenizer.from_pretrained(ruta_onnx)
    ort_model = ORTModelForFeatureExtraction.from_pretrained(ruta_onnx)

    catalogo_movil = []
    for doc in documentos:
        texto_vector = f"{doc['nombre']} {' '.join(doc['tags'])} {doc['texto']}"
        vector = generar_embedding_onnx(texto_vector, tokenizer, ort_model)

        catalogo_movil.append({
            "id": doc["id"],
            "nombre": doc["nombre"],
            "tags": doc["tags"],
            "precio_mxn": doc["precio_mxn"],
            "texto": doc["texto"],
            "embedding": vector,
        })

    ruta_salida.parent.mkdir(parents=True, exist_ok=True)
    with ruta_salida.open("w", encoding="utf-8") as f:
        json.dump(catalogo_movil, f, ensure_ascii=False)

    print(f"Catálogo móvil exportado en: {ruta_salida}")


if __name__ == "__main__":
    construir_catalogo_movil()