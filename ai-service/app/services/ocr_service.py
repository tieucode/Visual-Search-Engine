import logging

import easyocr
import numpy as np
from PIL import Image

from app.core.config import Settings
from app.services.types import OCRDetection, OCRResult

logger = logging.getLogger(__name__)


class OCRService:
    def __init__(self, settings: Settings) -> None:
        languages = [item.strip() for item in settings.ocr_languages.split(",") if item.strip()]
        if not languages:
            raise ValueError("OCR_LANGUAGES không được để trống")
        logger.info("Loading EasyOCR languages=%s", languages)
        self.reader = easyocr.Reader(languages, gpu=False)

    def process(self, image: Image.Image) -> OCRResult:
        if image is None:
            raise ValueError("Image không được để trống")
        converted = image.convert("RGB") if image.mode != "RGB" else image
        try:
            results = self.reader.readtext(np.array(converted), detail=1)
        finally:
            if converted is not image:
                converted.close()

        detections = []
        for result in results:
            if len(result) < 3 or not result[1] or not result[1].strip():
                continue
            detections.append(OCRDetection(
                text=result[1].strip(),
                confidence=float(result[2]),
                bounding_box=np.asarray(result[0]).tolist(),
            ))
        return OCRResult(" ".join(item.text for item in detections), detections)
