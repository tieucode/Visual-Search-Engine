import asyncio
import logging
import time
from io import BytesIO

from fastapi import APIRouter, File, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError

from app.core.config import settings
from app.api.dependencies import RuntimeDependency
from app.schemas.process import ProcessResponse

logger = logging.getLogger(__name__)

router = APIRouter(
    prefix="/api",
    tags=["Image Processing"],
)

processing_semaphore = asyncio.Semaphore(
    settings.max_active_processing
)

admission_lock = asyncio.Lock()
in_flight_requests = 0

async def try_admit_request() -> bool:
    """Chỉ tiếp nhận số request nằm trong giới hạn."""
    global in_flight_requests

    async with admission_lock:
        if in_flight_requests >= settings.max_in_flight_requests:
            return False

        in_flight_requests += 1
        return True

async def release_request_slot() -> None:
    """Giải phóng một vị trí request."""
    global in_flight_requests

    async with admission_lock:
        in_flight_requests -= 1

@router.post("/process-image", response_model=ProcessResponse)
async def process_image(runtime: RuntimeDependency, file: UploadFile = File(...)):
    start_time = time.perf_counter()

    admitted = False
    processing_acquired = False
    processing_task = None

    try:
        if not file.filename:
            raise HTTPException(
                status_code=400,
                detail="Tên file không hợp lệ",
            )

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

        admitted = await try_admit_request()

        if not admitted:
            raise HTTPException(
                status_code=503,
                detail=(
                    "AI Service đang quá tải. "
                    "Vui lòng thử lại sau."
                ),
                headers={"Retry-After": "5"},
            )

        try:
            await asyncio.wait_for(
                processing_semaphore.acquire(),
                timeout=settings.max_queue_wait_seconds,
            )
            processing_acquired = True

        except asyncio.TimeoutError:
            raise HTTPException(
                status_code=503,
                detail="Hết thời gian chờ AI Service.",
                headers={"Retry-After": "5"},
            )

        image_bytes = await file.read(
            settings.max_upload_size_bytes + 1
        )

        if not image_bytes:
            raise HTTPException(
                status_code=400,
                detail="File ảnh rỗng",
            )

        if len(image_bytes) > settings.max_upload_size_bytes:
            raise HTTPException(
                status_code=413,
                detail=(
                    "Ảnh vượt quá giới hạn "
                    f"{settings.max_upload_size_bytes / (1024 * 1024):g} MB."
                ),
            )

        try:
            with Image.open(BytesIO(image_bytes)) as source:
                width, height = source.size

                if width * height > settings.max_image_pixels:
                    raise HTTPException(
                        status_code=413,
                        detail=(
                            "Kích thước ảnh vượt quá "
                            f"{settings.max_image_pixels / 1_000_000:g} megapixel."
                        ),
                    )

                source.load()
                image = source.convert("RGB")

        except HTTPException:
            raise

        except (
            UnidentifiedImageError,
            Image.DecompressionBombError,
            OSError,
        ):
            raise HTTPException(
                status_code=400,
                detail="Không thể đọc file ảnh",
            )

        processing_task = asyncio.create_task(
            asyncio.to_thread(
                runtime.processor.process,
                image=image,
            )
        )

        try:
            result = await asyncio.shield(processing_task)

        except asyncio.CancelledError:
            try:
                await processing_task
            except Exception:
                pass

            raise

        total_duration_ms = round(
            (time.perf_counter() - start_time) * 1000
        )

        return {
            "success": True,
            "filename": file.filename,
            "embedding": result.embedding,
            "ocr": result.ocr,
            "total_duration_ms": total_duration_ms,
        }

    except HTTPException:
        raise

    except ValueError as e:
        raise HTTPException(
            status_code=400,
            detail=str(e),
        )

    except Exception as e:
        logger.exception("Image processing failed")

        raise HTTPException(
            status_code=500,
            detail=f"Lỗi xử lý ảnh: {str(e)}",
        )

    finally:
        if processing_acquired:
            processing_semaphore.release()

        if admitted:
            await release_request_slot()

        await file.close()
