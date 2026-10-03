from fastapi import APIRouter, File, HTTPException, UploadFile
from pydantic import BaseModel

from app.services.image_preprocessing import preprocess_image
from app.services.embedding_service import embedding_service

router = APIRouter(
    prefix="/api/embedding",
    tags=["Embedding"],
)


# ==========================================
# Request model cho Text Embedding
# ==========================================


class TextEmbeddingRequest(BaseModel):
    text: str


# ==========================================
# Image Embedding
# ==========================================


@router.post("/image")
async def image_embedding(file: UploadFile = File(...)):
    # Kiểm tra tên file
    if not file.filename:
        raise HTTPException(status_code=400, detail="Tên file không hợp lệ")

    # Các định dạng ảnh cho phép
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
        # Đọc file
        image_bytes = await file.read()

        if not image_bytes:
            raise HTTPException(status_code=400, detail="File ảnh rỗng")

        # Preprocessing
        image = preprocess_image(image_bytes)

        # Image -> Embedding
        embedding = embedding_service.embed_image(image)

        return {
            "success": True,
            "filename": file.filename,
            "dimension": len(embedding),
            "embedding": embedding,
        }

    except ValueError as e:

        raise HTTPException(status_code=400, detail=str(e))

    except Exception as e:

        raise HTTPException(
            status_code=500, detail=f"Lỗi tạo image embedding: {str(e)}"
        )


# ==========================================
# Text Embedding
# ==========================================


@router.post("/text")
async def text_embedding(request: TextEmbeddingRequest):
    # Kiểm tra text
    if not request.text or not request.text.strip():
        raise HTTPException(status_code=400, detail="Text không được để trống")

    try:
        # Text -> Embedding
        embedding = embedding_service.embed_text(request.text.strip())

        return {
            "success": True,
            "text": request.text.strip(),
            "dimension": len(embedding),
            "embedding": embedding,
        }

    except ValueError as e:

        raise HTTPException(status_code=400, detail=str(e))

    except Exception as e:

        raise HTTPException(status_code=500, detail=f"Lỗi tạo text embedding: {str(e)}")
