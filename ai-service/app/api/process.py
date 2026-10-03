from io import BytesIO

from fastapi import APIRouter, File, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError

from app.services.image_processor import image_processor

router = APIRouter(
    prefix="/api",
    tags=["Image Processing"],
)


@router.post("/process-image")
async def process_image(file: UploadFile = File(...)):

    # ==========================================
    # 1. Kiểm tra file
    # ==========================================

    if not file.filename:
        raise HTTPException(
            status_code=400,
            detail="Tên file không hợp lệ",
        )

    allowed_content_types = {
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/bmp",
        "image/gif",
    }

    if file.content_type not in allowed_content_types:
        raise HTTPException(
            status_code=400,
            detail=(
                "Định dạng ảnh không được hỗ trợ. "
                "Chỉ hỗ trợ JPG, PNG, WEBP, BMP, GIF."
            ),
        )

    try:
        # ==========================================
        # 2. Đọc file
        # ==========================================

        image_bytes = await file.read()

        if not image_bytes:
            raise HTTPException(
                status_code=400,
                detail="File ảnh rỗng",
            )

        # ==========================================
        # 3. Decode ảnh bằng PIL
        # ==========================================

        try:
            image = Image.open(BytesIO(image_bytes))
            image.load()

        except (UnidentifiedImageError, OSError):
            raise HTTPException(
                status_code=400,
                detail="Không thể đọc file ảnh",
            )

        # Đảm bảo RGB
        if image.mode != "RGB":
            image = image.convert("RGB")

        # ==========================================
        # 4. Gọi Image Processor
        # ==========================================

        result = image_processor.process(
            image=image,
        )

        # ==========================================
        # 5. Trả kết quả
        # ==========================================

        return {
            "success": True,
            "filename": file.filename,
            "embedding": result.get("embedding"),
            "ocr": result.get("ocr", []),
        }

    except HTTPException:
        raise

    except ValueError as e:
        raise HTTPException(
            status_code=400,
            detail=str(e),
        )

    except Exception as e:
        print(f"Lỗi process-image: {e}")

        raise HTTPException(
            status_code=500,
            detail=f"Lỗi xử lý ảnh: {str(e)}",
        )
