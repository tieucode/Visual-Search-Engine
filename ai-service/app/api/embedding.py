from fastapi import APIRouter, File, UploadFile

from app.api.dependencies import RuntimeDependency
from app.api.errors import api_errors
from app.api.uploads import uploaded_image
from app.core.errors import ServiceError
from app.schemas.embedding import ImageEmbeddingResponse, TextEmbeddingRequest, TextEmbeddingResponse

router = APIRouter(prefix="/api/embedding", tags=["Embedding"])


@router.post("/image", response_model=ImageEmbeddingResponse)
async def image_embedding(runtime: RuntimeDependency, file: UploadFile = File(...)):
    async def work():
        async with uploaded_image(file, runtime, priority="search") as image:
            return await runtime.embed_image(image, "search")

    with api_errors("image_embedding"):
        try:
            embedding = await runtime.run("search", work)
            return ImageEmbeddingResponse(
                filename=file.filename, dimension=len(embedding), embedding=embedding,
            )
        finally:
            await file.close()


@router.post("/text", response_model=TextEmbeddingResponse)
async def text_embedding(request: TextEmbeddingRequest, runtime: RuntimeDependency):
    with api_errors("text_embedding"):
        text = request.text.strip()
        if not text:
            raise ServiceError("Text không được để trống", code="EMPTY_TEXT")
        embedding = await runtime.run("search", lambda: runtime.embed_text(text, "search"))
        return TextEmbeddingResponse(text=text, dimension=len(embedding), embedding=embedding)
