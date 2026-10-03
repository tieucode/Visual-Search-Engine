from pathlib import Path

from app.services.image_preprocessing import preprocess_image
from app.services.ocr_service import ocr_service


# =========================
# Load image
# =========================

image_path = Path("images.png")

with open(image_path, "rb") as f:
    image_bytes = f.read()


# =========================
# Preprocessing
# =========================

image = preprocess_image(image_bytes)


# =========================
# OCR
# =========================

text = ocr_service.extract_text(image)


print("===== OCR TEST =====")
print("Status: SUCCESS")
print("OCR Text:")
print(text)