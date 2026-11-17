from pydantic import BaseModel


class TextEmbeddingRequest(BaseModel):
    text: str


class EmbeddingResponse(BaseModel):
    success: bool = True
    dimension: int
    embedding: list[float]


class ImageEmbeddingResponse(EmbeddingResponse):
    filename: str


class TextEmbeddingResponse(EmbeddingResponse):
    text: str
