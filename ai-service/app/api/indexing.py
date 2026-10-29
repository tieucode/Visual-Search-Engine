import time
from typing import Annotated

from fastapi import APIRouter, File, Form, UploadFile

from app.api.dependencies import RuntimeDependency
from app.api.errors import api_errors, failed_item
from app.api.uploads import uploaded_image, validate_batch
from app.schemas.indexing import BatchItem, BatchResponse

router = APIRouter(prefix="/api/v1/indexing", tags=["Indexing"])


@router.post("/batch", response_model=BatchResponse)
async def batch_indexing(
    runtime: RuntimeDependency,
    imageIds: Annotated[list[str], Form(...)],
    images: Annotated[list[UploadFile], File(...)],
):
    with api_errors("batch_indexing"):
        try:
            budget = validate_batch(imageIds, images, runtime.settings)
            results = []
            for image_id, file in zip(imageIds, images):
                started = time.perf_counter()

                async def work():
                    async with uploaded_image(
                        file, runtime, priority="background", resize=False, budget=budget,
                    ) as image:
                        return await runtime.process(image, "background")

                try:
                    result = await runtime.run("background", work)
                    results.append(BatchItem(
                        imageId=image_id, status="SUCCESS",
                        processingTimeMs=round((time.perf_counter() - started) * 1000, 2),
                        embedding=result.embedding, ocr=result.ocr,
                    ))
                except Exception as exc:
                    results.append(failed_item(
                        image_id, round((time.perf_counter() - started) * 1000, 2), exc,
                    ))
                finally:
                    await file.close()
            return BatchResponse(results=results)
        finally:
            for file in images:
                await file.close()
