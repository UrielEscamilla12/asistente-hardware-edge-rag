"""Exporta el modelo de embeddings a formato ONNX y guarda su tokenizador."""

from pathlib import Path
from optimum.onnxruntime import ORTModelForFeatureExtraction
from transformers import AutoTokenizer


MODEL_ID = "google/embeddinggemma-300m"
OUTPUT_DIR = Path("../models/export/embeddinggemma_onnx")


def exportar_modelo_onnx(model_id: str = MODEL_ID, output_dir: Path = OUTPUT_DIR) -> None:
    """Convierte el modelo base a ONNX y almacena los artefactos de tokenización."""
    output_dir.mkdir(parents=True, exist_ok=True)
    print(f"Exportando '{model_id}' a formato ONNX en '{output_dir}'...")

    ort_model = ORTModelForFeatureExtraction.from_pretrained(model_id, export=True)
    tokenizer = AutoTokenizer.from_pretrained(model_id)

    ort_model.save_pretrained(output_dir)
    tokenizer.save_pretrained(output_dir)
    print("Exportación ONNX completada.")


if __name__ == "__main__":
    exportar_modelo_onnx()