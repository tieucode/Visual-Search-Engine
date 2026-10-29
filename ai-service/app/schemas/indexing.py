from typing import Literal

from pydantic import BaseModel, Field

from app.services.types import IndexedOCR


class BatchError(BaseModel):
    code: str
    message: str
    retryable: bool


class BatchItem(BaseModel):
    imageId: str
    status: Literal["SUCCESS", "FAILED"]
    processingTimeMs: float
    embedding: list[float] | None = None
    ocr: list[IndexedOCR] = Field(default_factory=list)
    error: BatchError | None = None


class BatchResponse(BaseModel):
    results: list[BatchItem]
