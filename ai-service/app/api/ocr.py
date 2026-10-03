from fastapi import APIRouter, File, HTTPException, UploadFile

from app.services.ocr_service import ocr_service
from app.services.image_preprocessing import preprocess_image


router = APIRouter(
    prefix="/api/ocr",
    tags=["OCR"]
)


@router.post("")
async def extract_text(
    file: UploadFile = File(...)
):

    # ==========================================
    # Validate filename
    # ==========================================

    if not file.filename:
        raise HTTPException(
            status_code=400,
            detail="Tên file không hợp lệ"
        )

    # ==========================================
    # Validate image type
    # ==========================================

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
            )
        )

    try:

        # ======================================
        # Read file
        # ======================================

        image_bytes = await file.read()

        if not image_bytes:
            raise HTTPException(
                status_code=400,
                detail="File ảnh rỗng"
            )

        # ======================================
        # Image preprocessing
        # ======================================

        image = preprocess_image(
            image_bytes
        )

        # ======================================
        # OCR
        # Chạy EasyOCR CHỈ 1 LẦN
        # ======================================

        result = ocr_service.process(
            image
        )

        # ======================================
        # Response
        # ======================================

        return {
            "success": True,
            "filename": file.filename,
            "text": result["text"],
            "detections": result["detections"],
        }

    except ValueError as e:

        raise HTTPException(
            status_code=400,
            detail=str(e)
        )

    except Exception as e:

        raise HTTPException(
            status_code=500,
            detail=f"Lỗi OCR: {str(e)}"
        )