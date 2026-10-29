import logging
from typing import TYPE_CHECKING

from PIL import Image

from app.core.config import Settings
from app.services.image_processor import ImageProcessor

if TYPE_CHECKING:
    from app.services.embedding_service import EmbeddingService
    from app.services.ocr_service import OCRService

logger = logging.getLogger(__name__)


class Runtime:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.ready = False
        self.embedding: EmbeddingService | None = None
        self.ocr: OCRService | None = None
        self.processor: ImageProcessor | None = None

    def start(self) -> None:
        # Import ML libraries only at startup, never while importing the application.
        from app.services.embedding_service import EmbeddingService
        from app.services.ocr_service import OCRService

        self.embedding = EmbeddingService(self.settings)
        self.ocr = OCRService(self.settings)
        self.processor = ImageProcessor(self.embedding, self.ocr)
        with Image.new("RGB", (224, 224), "white") as image:
            self.processor.process(image)
        self.embedding.embed_text("warmup")
        self.ready = True
        logger.info("AI models initialized and warmed up")

    def close(self) -> None:
        self.ready = False
        self.processor = None
        self.embedding = None
        self.ocr = None
