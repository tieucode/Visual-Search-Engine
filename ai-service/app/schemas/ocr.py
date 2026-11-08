from pydantic import BaseModel

from app.services.types import OCRDetection


class OCRResponse(BaseModel):
    success: bool = True
    filename: str
    text: str
    detections: list[OCRDetection]
