import logging
from contextlib import contextmanager

from fastapi import HTTPException

from app.core.errors import ServiceError
from app.schemas.indexing import BatchError, BatchItem

logger = logging.getLogger(__name__)


@contextmanager
def api_errors(operation: str):
    try:
        yield
    except HTTPException:
        raise
    except ServiceError as exc:
        headers = {"Retry-After": "5"} if exc.retryable else None
        raise HTTPException(exc.status_code, str(exc), headers=headers) from exc
    except Exception as exc:
        logger.exception("AI operation failed operation=%s", operation)
        raise HTTPException(500, "Lỗi xử lý AI") from exc


def failed_item(image_id: str, elapsed_ms: float, exc: Exception) -> BatchItem:
    if isinstance(exc, ServiceError):
        error = BatchError(code=exc.code, message=str(exc), retryable=exc.retryable)
    else:
        logger.exception("Batch image processing failed")
        error = BatchError(code="PROCESSING_ERROR", message="Image processing failed", retryable=True)
    return BatchItem(
        imageId=image_id, status="FAILED", processingTimeMs=elapsed_ms, error=error,
    )
