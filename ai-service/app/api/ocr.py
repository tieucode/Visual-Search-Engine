from fastapi import APIRouter, File, HTTPException, UploadFile

from app.api.dependencies import RuntimeDependency
from app.schemas.ocr import OCRResponse
from app.services.image_preprocessing import preprocess_image

router = APIRouter(
    prefix="/api/ocr",
    tags=["OCR"]
)

@router.post("", response_model=OCRResponse)
async def extract_text(
    runtime: RuntimeDependency,
    file: UploadFile = File(...)
):

    if not file.filename:
        raise HTTPException(
            status_code=400,
            detail="Tên file không hợp lệ"
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
            )
        )

    try:

        image_bytes = await file.read()

        if not image_bytes:
            raise HTTPException(
                status_code=400,
                detail="File ảnh rỗng"
            )

        image = preprocess_image(
            image_bytes
        )

        result = runtime.ocr.process(
            image
        )

        return {
            "success": True,
            "filename": file.filename,
            "text": result.text,
            "detections": result.detections,
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
