from typing import Any, Dict

from app.services.image_preprocessing import preprocess_image
from app.services.embedding_service import embedding_service
from app.services.ocr_service import ocr_service


class ImageProcessor:
    def __init__(self):
        self.embedding_service = embedding_service
        self.ocr_service = ocr_service

    def process_image(
        self,
        image_bytes: bytes,
    ) -> Dict[str, Any]:
        if not image_bytes:
            raise ValueError("Image không được để trống")

        # ==========================================
        # 1. Image Preprocessing
        # ==========================================

        image = preprocess_image(image_bytes)

        # ==========================================
        # 2. Image Embedding bằng SigLIP
        # ==========================================

        embedding = self.embedding_service.embed_image(image)

        # ==========================================
        # 3. OCR bằng EasyOCR
        # ==========================================

        ocr_text = self.ocr_service.extract_text(image)

        # ==========================================
        # 4. Trả kết quả
        # ==========================================

        return {
            "embedding": embedding,
            "ocr_text": ocr_text,
        }


# Singleton
image_processor = ImageProcessor()
