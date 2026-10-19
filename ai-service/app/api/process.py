import asyncio
import time
from io import BytesIO

from fastapi import APIRouter, File, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError

from app.core.config import settings
from app.services.image_processor import image_processor

router = APIRouter(
    prefix="/api",
    tags=["Image Processing"],
)

# Giới hạn số tác vụ xử lý AI đồng thời
processing_semaphore = asyncio.Semaphore(
    settings.max_active_processing
)

# Quản lý tổng số request đang xử lý hoặc chờ
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


@router.post("/process-image")
async def process_image(file: UploadFile = File(...)):
    start_time = time.perf_counter()
    
    admitted = False
    processing_acquired = False
    processing_task = None
    
    try:
        # ==========================================
        # 1. Kiểm tra file
        # ==========================================
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
            
        # Chờ đến lượt xử lý AI
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

        # Đọc ảnh có giới hạn dung lượng
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

        # Đọc và kiểm tra ảnh
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

        # Chạy xử lý AI ngoài event loop
        # Shield giúp tác vụ nền không bị hủy khi client ngắt kết nối.
        processing_task = asyncio.create_task(
            asyncio.to_thread(
                image_processor.process,
                image=image,
            )
        )

        try:
            result = await asyncio.shield(processing_task)

        except asyncio.CancelledError:
            # Chờ tác vụ nền kết thúc trước khi nhả semaphore,
            # tránh một tác vụ khác chạy đồng thời ngoài ý muốn.
            try:
                await processing_task
            except Exception:
                pass

            raise

        # ==========================================
        # Tính tổng thời gian, đơn vị milliseconds
        # ==========================================
        
        total_duration_ms = round(
            (time.perf_counter() - start_time) * 1000
        )
        
        # ==========================================
        # Trả kết quả
        # ==========================================

        return {
            "success": True,
            "filename": file.filename,
            "embedding": result.get("embedding"),
            "ocr": result.get("ocr", []),
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
        print(f"Lỗi process-image: {e}")

        raise HTTPException(
            status_code=500,
            detail=f"Lỗi xử lý ảnh: {str(e)}",
        )
        
    finally:
        # Chỉ giải phóng những tài nguyên đã được lấy
        if processing_acquired:
            processing_semaphore.release()

        if admitted:
            await release_request_slot()

        await file.close()
