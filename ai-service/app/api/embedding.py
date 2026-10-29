from fastapi import APIRouter, File, UploadFile

from app.api.dependencies import RuntimeDependency
from app.api.errors import api_errors
from app.api.uploads import uploaded_image
from app.core.errors import ServiceError
from app.schemas.embedding import ImageEmbeddingResponse, TextEmbeddingRequest, TextEmbeddingResponse

router = APIRouter(prefix="/api/embedding", tags=["Embedding"])


@router.post("/image", response_model=ImageEmbeddingResponse)
async def image_embedding(runtime: RuntimeDependency, file: UploadFile = File(...)):
    with api_errors("image_embedding"):
        async with uploaded_image(file, runtime.settings) as image:
            embedding = runtime.embedding.embed_image(image)
        return ImageEmbeddingResponse(
            filename=file.filename, dimension=len(embedding), embedding=embedding,
        )


@router.post("/text", response_model=TextEmbeddingResponse)
async def text_embedding(request: TextEmbeddingRequest, runtime: RuntimeDependency):
    with api_errors("text_embedding"):
        text = request.text.strip()
        if not text:
            raise ServiceError("Text không được để trống", code="EMPTY_TEXT")
        embedding = runtime.embedding.embed_text(text)
        return TextEmbeddingResponse(text=text, dimension=len(embedding), embedding=embedding)
