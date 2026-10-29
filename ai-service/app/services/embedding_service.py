import logging

import torch
from PIL import Image
from transformers import AutoModel, AutoProcessor

from app.core.config import Settings

logger = logging.getLogger(__name__)


class EmbeddingService:
    def __init__(self, settings: Settings) -> None:
        self.device = torch.device("cpu")
        logger.info("Loading SigLIP model=%s device=%s", settings.siglip_model, self.device)
        self.processor = AutoProcessor.from_pretrained(settings.siglip_model)
        self.model = AutoModel.from_pretrained(settings.siglip_model)
        self.model.to(self.device)
        self.model.eval()

    @staticmethod
    def _normalize(output) -> list[list[float]]:
        features = output.pooler_output if hasattr(output, "pooler_output") else output
        return torch.nn.functional.normalize(features, p=2, dim=-1).cpu().tolist()

    def embed_image(self, image: Image.Image) -> list[float]:
        if image is None:
            raise ValueError("Image không được để trống")
        converted = image.convert("RGB") if image.mode != "RGB" else image
        try:
            inputs = self.processor(images=converted, return_tensors="pt")
        finally:
            if converted is not image:
                converted.close()
        inputs = {key: value.to(self.device) for key, value in inputs.items()}
        with torch.no_grad():
            return self._normalize(self.model.get_image_features(**inputs))[0]

    def embed_text(self, text: str) -> list[float]:
        if not text or not text.strip():
            raise ValueError("Text không được để trống")
        inputs = self.processor(
            text=text, return_tensors="pt", padding="max_length", truncation=True,
        )
        inputs = {key: value.to(self.device) for key, value in inputs.items()}
        with torch.no_grad():
            return self._normalize(self.model.get_text_features(**inputs))[0]
