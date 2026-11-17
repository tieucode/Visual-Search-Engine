import time

from fastapi import APIRouter, File, UploadFile

from app.api.dependencies import RuntimeDependency
from app.api.errors import api_errors
from app.api.uploads import uploaded_image
from app.schemas.process import ProcessResponse

router = APIRouter(prefix="/api", tags=["Image Processing"])


@router.post("/process-image", response_model=ProcessResponse)
async def process_image(runtime: RuntimeDependency, file: UploadFile = File(...)):
    started = time.perf_counter()

    async def work():
        async with uploaded_image(file, runtime, priority="background", resize=False) as image:
            return await runtime.process(image, "background")

    with api_errors("process_image"):
        try:
            result = await runtime.run("background", work)
            return ProcessResponse(
                filename=file.filename, embedding=result.embedding, ocr=result.ocr,
                total_duration_ms=round((time.perf_counter() - started) * 1000),
            )
        finally:
            await file.close()
