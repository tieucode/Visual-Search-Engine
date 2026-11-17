from contextlib import asynccontextmanager
from dataclasses import dataclass
from typing import TYPE_CHECKING

from fastapi import UploadFile

from app.core.config import Settings
from app.core.errors import ServiceError
from app.core.execution import Priority
from app.services.image_preprocessing import validate_upload

if TYPE_CHECKING:
    from app.core.runtime import Runtime


@dataclass
class UploadBudget:
    limit: int
    used: int = 0

    def consume(self, count: int) -> None:
        self.used += count
        if self.used > self.limit:
            raise ServiceError("Batch vượt quá giới hạn dung lượng", code="BATCH_TOO_LARGE", status_code=413)


def validate_batch(image_ids: list[str], files: list[UploadFile], settings: Settings) -> UploadBudget:
    if len(image_ids) != len(files):
        raise ServiceError("Số lượng imageIds không khớp số lượng images", code="BATCH_COUNT_MISMATCH")
    if not files or len(files) > settings.max_batch_images:
        raise ServiceError("Số lượng ảnh trong batch không hợp lệ", code="BATCH_TOO_LARGE", status_code=413)
    budget = UploadBudget(settings.max_batch_upload_size_bytes)
    if sum(file.size or 0 for file in files) > budget.limit:
        raise ServiceError("Batch vượt quá giới hạn dung lượng", code="BATCH_TOO_LARGE", status_code=413)
    return budget


async def read_upload(
    file: UploadFile, settings: Settings, *, batch: bool = False, budget: UploadBudget | None = None,
) -> bytes:
    validate_upload(file.filename, file.content_type, batch=batch)
    data = await file.read(settings.max_upload_size_bytes + 1)
    if budget is not None:
        budget.consume(len(data))
    if not data:
        raise ServiceError("File ảnh rỗng", code="EMPTY_FILE")
    if len(data) > settings.max_upload_size_bytes:
        raise ServiceError("Ảnh vượt quá giới hạn dung lượng", code="IMAGE_TOO_LARGE", status_code=413)
    return data


@asynccontextmanager
async def uploaded_image(
    file: UploadFile, runtime: "Runtime", *, priority: Priority, resize: bool = True,
    budget: UploadBudget | None = None,
):
    try:
        data = await read_upload(file, runtime.settings, batch=budget is not None, budget=budget)
        image = await runtime.decode(data, priority=priority, resize=resize)
        del data
        try:
            yield image
        finally:
            image.close()
    finally:
        await file.close()
