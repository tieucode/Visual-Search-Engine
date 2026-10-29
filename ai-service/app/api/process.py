import asyncio
import time

from fastapi import APIRouter, File, UploadFile

from app.api.dependencies import RuntimeDependency
from app.api.errors import api_errors
from app.api.uploads import uploaded_image
from app.core.config import settings
from app.core.errors import ServiceError
from app.schemas.process import ProcessResponse

router = APIRouter(prefix="/api", tags=["Image Processing"])
processing_semaphore = asyncio.Semaphore(settings.max_active_processing)
in_flight_requests = 0


@router.post("/process-image", response_model=ProcessResponse)
async def process_image(runtime: RuntimeDependency, file: UploadFile = File(...)):
    global in_flight_requests
    started = time.perf_counter()
    admitted = acquired = False
    with api_errors("process_image"):
        try:
            if in_flight_requests >= settings.max_in_flight_requests:
                raise ServiceError("AI Service đang quá tải", code="OVERLOADED", status_code=503, retryable=True)
            in_flight_requests += 1
            admitted = True
            try:
                await asyncio.wait_for(processing_semaphore.acquire(), settings.max_queue_wait_seconds)
                acquired = True
            except TimeoutError as exc:
                raise ServiceError("Hết thời gian chờ AI Service", code="QUEUE_TIMEOUT", status_code=503, retryable=True) from exc
            async with uploaded_image(file, runtime.settings, resize=False) as image:
                task = asyncio.create_task(asyncio.to_thread(runtime.processor.process, image))
                try:
                    result = await asyncio.shield(task)
                except asyncio.CancelledError:
                    while not task.done():
                        try:
                            await asyncio.shield(task)
                        except asyncio.CancelledError:
                            continue
                        except Exception:
                            break
                    if not task.cancelled():
                        task.exception()
                    raise
            return ProcessResponse(
                filename=file.filename, embedding=result.embedding, ocr=result.ocr,
                total_duration_ms=round((time.perf_counter() - started) * 1000),
            )
        finally:
            if acquired:
                processing_semaphore.release()
            if admitted:
                in_flight_requests -= 1
            await file.close()
