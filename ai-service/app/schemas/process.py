from pydantic import BaseModel

from app.services.types import IndexedOCR


class ProcessResponse(BaseModel):
    success: bool = True
    filename: str
    embedding: list[float]
    ocr: list[IndexedOCR]
    total_duration_ms: int
