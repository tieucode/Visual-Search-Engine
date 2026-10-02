from fastapi import APIRouter, File, HTTPException, UploadFile

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
        raise HTTPException(status_code=400, detail="Tên file không hợp lệ")

    # Kiểm tra content type
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
        # 2. Đọc file thành bytes
        # ==========================================

        image_bytes = await file.read()

        if not image_bytes:
            raise HTTPException(status_code=400, detail="File ảnh rỗng")

        # ==========================================
        # 3. Gọi Image Processor
        # ==========================================

        result = image_processor.process_image(image_bytes)

        # ==========================================
        # 4. Trả kết quả
        # ==========================================

        return {
            "success": True,
            "filename": file.filename,
            "embedding": result["embedding"],
            "ocr_text": result["ocr_text"],
        }

    except ValueError as e:

        raise HTTPException(status_code=400, detail=str(e))

    except Exception as e:

        raise HTTPException(status_code=500, detail=f"Lỗi xử lý ảnh: {str(e)}")
