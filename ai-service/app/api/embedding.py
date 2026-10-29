from fastapi import APIRouter, File, HTTPException, UploadFile
from app.api.dependencies import RuntimeDependency
from app.schemas.embedding import ImageEmbeddingResponse, TextEmbeddingRequest, TextEmbeddingResponse

from app.services.image_preprocessing import preprocess_image

router = APIRouter(
    prefix="/api/embedding",
    tags=["Embedding"],
)

@router.post("/image", response_model=ImageEmbeddingResponse)
async def image_embedding(runtime: RuntimeDependency, file: UploadFile = File(...)):
    if not file.filename:
        raise HTTPException(status_code=400, detail="Tên file không hợp lệ")

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
        image_bytes = await file.read()

        if not image_bytes:
            raise HTTPException(status_code=400, detail="File ảnh rỗng")

        image = preprocess_image(image_bytes)

        embedding = runtime.embedding.embed_image(image)

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

@router.post("/text", response_model=TextEmbeddingResponse)
async def text_embedding(request: TextEmbeddingRequest, runtime: RuntimeDependency):
    if not request.text or not request.text.strip():
        raise HTTPException(status_code=400, detail="Text không được để trống")

    try:
        embedding = runtime.embedding.embed_text(request.text.strip())

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
