import logging
from contextlib import ExitStack
from pathlib import Path

import torch
from PIL import Image
from transformers import AutoModel, AutoProcessor

from app.core.config import Settings

logger = logging.getLogger(__name__)


class EmbeddingService:
    def __init__(self, settings: Settings) -> None:
        self.device = torch.device(settings.device)
        cache = str(Path(settings.model_cache_dir).expanduser() / "huggingface" / "hub")
        logger.info("Loading SigLIP model=%s device=%s", settings.siglip_model, self.device)
        self.processor = AutoProcessor.from_pretrained(settings.siglip_model, cache_dir=cache)
        self.model = AutoModel.from_pretrained(
            settings.siglip_model, cache_dir=cache, torch_dtype=torch.float32,
        )
        self.model.to(self.device)
        self.model.eval()

    @staticmethod
    def _normalize(output, expected_count: int) -> list[list[float]]:
        features = output.pooler_output if hasattr(output, "pooler_output") else output
        if not isinstance(features, torch.Tensor) or features.shape != (expected_count, 768):
            raise RuntimeError("SigLIP returned an unexpected embedding shape")
        features = torch.nn.functional.normalize(features.float(), p=2, dim=-1)
        if not torch.isfinite(features).all() or (features.norm(dim=-1) == 0).any():
            raise RuntimeError("SigLIP returned invalid embedding values")
        return features.cpu().tolist()

    def embed_image(self, image: Image.Image) -> list[float]:
        return self.embed_images([image])[0]

    def embed_images(self, images: list[Image.Image]) -> list[list[float]]:
        if not images:
            return []
        with ExitStack() as stack:
            rgb_images = []
            for image in images:
                if image is None:
                    raise ValueError("Image không được để trống")
                if image.mode != "RGB":
                    image = stack.enter_context(image.convert("RGB"))
                rgb_images.append(image)
            with torch.inference_mode():
                inputs = self.processor(images=rgb_images, return_tensors="pt")
                inputs = {key: value.to(self.device) for key, value in inputs.items()}
                return self._normalize(self.model.get_image_features(**inputs), len(images))

    def embed_text(self, text: str) -> list[float]:
        if not text or not text.strip():
            raise ValueError("Text không được để trống")
        with torch.inference_mode():
            inputs = self.processor(
                text=text, return_tensors="pt", padding="max_length", truncation=True,
                max_length=self.model.config.text_config.max_position_embeddings,
            )
            inputs = {key: value.to(self.device) for key, value in inputs.items()}
            return self._normalize(self.model.get_text_features(**inputs), 1)[0]
