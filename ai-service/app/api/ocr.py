from fastapi import APIRouter, File, UploadFile

from app.api.dependencies import RuntimeDependency
from app.api.errors import api_errors
from app.api.uploads import uploaded_image
from app.schemas.ocr import OCRResponse

router = APIRouter(prefix="/api/ocr", tags=["OCR"])


@router.post("", response_model=OCRResponse)
async def extract_text(runtime: RuntimeDependency, file: UploadFile = File(...)):
    async def work():
        async with uploaded_image(file, runtime, priority="background") as image:
            return await runtime.extract_ocr(image, "background")

    with api_errors("ocr"):
        try:
            result = await runtime.run("background", work)
            return OCRResponse(filename=file.filename, text=result.text, detections=result.detections)
        finally:
            await file.close()
