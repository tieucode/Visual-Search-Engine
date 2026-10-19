import time
from typing import Any, Dict, Optional
from PIL import Image

from app.services.embedding_service import embedding_service
from app.services.ocr_service import ocr_service
from app.utils.text_normalizer import normalize_text


class ImageProcessor:

    def process(
        self,
        image: Image.Image,
        image_id: Optional[str] = None,
    ) -> Dict[str, Any]:

         # Bắt đầu đo thời gian xử lý AI
        start_time = time.perf_counter()

        # SigLIP
        embedding = embedding_service.embed_image(image)

        # EasyOCR
        ocr_result = ocr_service.process(image)

        ocr_data = []

        for detection in ocr_result["detections"]:
            bounding_box = detection["bounding_box"]

            xs = [point[0] for point in bounding_box]
            ys = [point[1] for point in bounding_box]

            x = min(xs)
            y = min(ys)
            width = max(xs) - x
            height = max(ys) - y

            text = detection["text"]

            ocr_data.append(
                {
                    "text": text,
                    "normalizedText": normalize_text(text),
                    "confidence": detection["confidence"],
                    "boundingBox": {
                        "x": float(x),
                        "y": float(y),
                        "width": float(width),
                        "height": float(height),
                    },
                }
            )
        
         # Tổng thời gian embedding + OCR + xử lý kết quả
        total_duration_ms = round(
            (time.perf_counter() - start_time) * 1000
        )

        return {
            "imageId": image_id,
            "status": "SUCCESS",
            "embedding": embedding,
            "ocr": ocr_data,
            "total_duration_ms": total_duration_ms,
            "error": None,
        }


image_processor = ImageProcessor()
