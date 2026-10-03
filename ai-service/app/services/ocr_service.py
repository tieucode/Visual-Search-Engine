from typing import List, Dict, Any

import numpy as np
import easyocr
from PIL import Image

from app.core.config import settings


class OCRService:

    def __init__(self):
        print("Loading EasyOCR model...")

        languages = [
            language.strip()
            for language in settings.ocr_languages.split(",")
            if language.strip()
        ]

        if not languages:
            raise ValueError(
                "OCR_LANGUAGES không được để trống"
            )

        self.languages = languages

        use_gpu = False

        self.reader = easyocr.Reader(
            languages,
            gpu=use_gpu,
        )

        print(
            f"EasyOCR loaded successfully. "
            f"Languages: {languages}"
        )

        print(
            f"EasyOCR device: "
            f"{'GPU' if use_gpu else 'CPU'}"
        )

    # ==================================================
    # PRIVATE
    # ==================================================

    def _read_text(self, image: Image.Image):
        """
        Chạy EasyOCR một lần.
        """

        if image is None:
            raise ValueError(
                "Image không được để trống"
            )

        if image.mode != "RGB":
            image = image.convert("RGB")

        image_array = np.array(image)

        results = self.reader.readtext(
            image_array,
            detail=1,
        )

        return results

    # ==================================================
    # TEXT ONLY
    # ==================================================

    def extract_text(
        self,
        image: Image.Image,
    ) -> str:

        results = self._read_text(image)

        texts = []

        for result in results:

            if len(result) < 2:
                continue

            text = result[1]

            if text and text.strip():
                texts.append(
                    text.strip()
                )

        return " ".join(texts)

    # ==================================================
    # TEXT + DETAILS
    # ==================================================

    def extract_text_with_details(
        self,
        image: Image.Image,
    ) -> List[Dict[str, Any]]:

        results = self._read_text(image)

        output = []

        for result in results:

            if len(result) < 3:
                continue

            bounding_box = result[0]
            text = result[1]
            confidence = result[2]

            if not text or not text.strip():
                continue

            # Chuyển NumPy array / numpy.int32
            # thành Python list + int
            bounding_box = np.asarray(
                bounding_box
            ).tolist()

            output.append(
                {
                    "text": text.strip(),

                    "confidence": float(
                        confidence
                    ),

                    "bounding_box": bounding_box,
                }
            )

        return output

    # ==================================================
    # PROCESS
    # ==================================================

    def process(
        self,
        image: Image.Image,
    ) -> Dict[str, Any]:
        """
        Chạy EasyOCR một lần.

        Trả về:

        {
            "text": "...",
            "detections": [...]
        }
        """

        results = self._read_text(image)

        texts = []
        detections = []

        for result in results:

            if len(result) < 3:
                continue

            bounding_box = result[0]
            text = result[1]
            confidence = result[2]

            if not text or not text.strip():
                continue

            clean_text = text.strip()

            # ======================================
            # FIX numpy.int32
            # ======================================

            bounding_box = np.asarray(
                bounding_box
            ).tolist()

            texts.append(clean_text)

            detections.append(
                {
                    "text": clean_text,

                    "confidence": float(
                        confidence
                    ),

                    "bounding_box": bounding_box,
                }
            )

        full_text = " ".join(texts)

        return {
            "text": full_text,
            "detections": detections,
        }


# ==================================================
# Singleton
# ==================================================

ocr_service = OCRService()