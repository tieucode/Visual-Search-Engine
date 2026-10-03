from pathlib import Path

from app.services.image_processor import image_processor


# ==========================================
# 1. Đọc ảnh
# ==========================================

image_path = Path("image2.png")

with open(image_path, "rb") as f:
    image_bytes = f.read()


# ==========================================
# 2. Process image
# ==========================================

result = image_processor.process_image(
    image_bytes
)


# ==========================================
# 3. In kết quả
# ==========================================

print("===== IMAGE PROCESSOR TEST =====")

print("Status: SUCCESS")

print(
    "Embedding dimension:",
    len(result["embedding"])
)

print(
    "First 10 embedding values:",
    result["embedding"][:10]
)

print(
    "OCR Text:",
    result["ocr_text"]
)