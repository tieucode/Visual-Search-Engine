from typing import TYPE_CHECKING

from PIL import Image

from app.services.types import BoundingBox, IndexedOCR, OCRResult, ProcessedImage
from app.utils.text_normalizer import normalize_text

if TYPE_CHECKING:
    from app.services.embedding_service import EmbeddingService
    from app.services.ocr_service import OCRService


class ImageProcessor:
    def __init__(self, embedding: "EmbeddingService", ocr: "OCRService") -> None:
        self.embedding = embedding
        self.ocr = ocr

    def process(self, image: Image.Image) -> ProcessedImage:
        embedding = self.embedding.embed_image(image)
        return ProcessedImage(embedding, self.indexed_ocr(self.ocr.process(image)))

    @staticmethod
    def indexed_ocr(result: OCRResult) -> list[IndexedOCR]:
        output = []
        for detection in result.detections:
            xs, ys = zip(*detection.bounding_box)
            x, y = min(xs), min(ys)
            output.append(IndexedOCR(
                text=detection.text,
                normalizedText=normalize_text(detection.text),
                confidence=detection.confidence,
                boundingBox=BoundingBox(
                    x=float(x), y=float(y),
                    width=float(max(xs) - x), height=float(max(ys) - y),
                ),
            ))
        return output
