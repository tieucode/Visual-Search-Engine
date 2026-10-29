import time
from contextlib import AsyncExitStack
from typing import Annotated

from fastapi import APIRouter, File, Form, UploadFile

from app.api.dependencies import RuntimeDependency
from app.api.errors import api_errors, failed_item
from app.api.uploads import UploadBudget, uploaded_image, validate_batch
from app.core.errors import ServiceError
from app.core.runtime import Runtime
from app.schemas.indexing import BatchItem, BatchResponse

router = APIRouter(prefix="/api/v1/indexing", tags=["Indexing"])


def elapsed_ms(started: float) -> float:
    return round((time.perf_counter() - started) * 1000, 2)


async def process_chunk(
    runtime: Runtime, image_ids: list[str], files: list[UploadFile], budget: UploadBudget,
) -> list[BatchItem]:
    started = [time.perf_counter() for _ in files]
    items: list[BatchItem | None] = [None] * len(files)
    async with AsyncExitStack() as stack:
        prepared = []
        positions = []
        for position, file in enumerate(files):
            started[position] = time.perf_counter()
            try:
                image = await stack.enter_async_context(uploaded_image(
                    file, runtime, priority="background", resize=False, budget=budget,
                ))
                prepared.append(image)
                positions.append(position)
            except Exception as exc:
                items[position] = failed_item(image_ids[position], elapsed_ms(started[position]), exc)

        try:
            outcomes = await runtime.process_batch(prepared)
        except Exception as exc:
            outcomes = [exc] * len(prepared)
        for position, outcome in zip(positions, outcomes):
            if isinstance(outcome, Exception):
                items[position] = failed_item(
                    image_ids[position], elapsed_ms(started[position]), outcome,
                )
            else:
                items[position] = BatchItem(
                    imageId=image_ids[position], status="SUCCESS",
                    processingTimeMs=elapsed_ms(started[position]),
                    embedding=outcome.embedding, ocr=outcome.ocr,
                )
    if any(item is None for item in items):
        raise RuntimeError("Missing batch image result")
    return [item for item in items if item is not None]


@router.post("/batch", response_model=BatchResponse)
async def batch_indexing(
    runtime: RuntimeDependency,
    imageIds: Annotated[list[str], Form(...)],
    images: Annotated[list[UploadFile], File(...)],
):
    with api_errors("batch_indexing"):
        try:
            budget = validate_batch(imageIds, images, runtime.settings)
            results: list[BatchItem] = []
            size = runtime.settings.embedding_microbatch_size
            for offset in range(0, len(images), size):
                chunk_ids = imageIds[offset:offset + size]
                chunk_files = images[offset:offset + size]
                started = time.perf_counter()
                try:
                    chunk = await runtime.run(
                        "background",
                        lambda: process_chunk(runtime, chunk_ids, chunk_files, budget),
                    )
                    results.extend(chunk)
                except ServiceError as exc:
                    if exc.status_code == 503 and not results:
                        raise
                    results.extend(failed_item(image_id, elapsed_ms(started), exc) for image_id in chunk_ids)
                finally:
                    for file in chunk_files:
                        await file.close()
            return BatchResponse(results=results)
        finally:
            for file in images:
                await file.close()
