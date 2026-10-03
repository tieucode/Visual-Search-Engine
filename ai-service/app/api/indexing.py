from typing import Annotated
import time
from io import BytesIO

from fastapi import APIRouter, File, Form, HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError

from app.services.image_processor import image_processor


router = APIRouter(
    prefix="/api/v1/indexing",
    tags=["Indexing"],
)


@router.post("/batch")
async def batch_indexing(
    imageIds: Annotated[list[str], Form(...)],
    images: Annotated[list[UploadFile], File(...)],
):
    if len(imageIds) != len(images):
        raise HTTPException(
            status_code=400,
            detail=(
                f"Số lượng imageIds ({len(imageIds)}) "
                f"không khớp số lượng images ({len(images)})"
            ),
        )

    results = []

    for image_id, file in zip(imageIds, images):

        start_time = time.perf_counter()

        try:
            image_bytes = await file.read()

            if not image_bytes:
                processing_time = round(
                    (time.perf_counter() - start_time) * 1000,
                    2,
                )

                results.append({
                    "imageId": image_id,
                    "status": "FAILED",
                    "processingTimeMs": processing_time,
                    "embedding": None,
                    "ocr": [],
                    "error": {
                        "code": "EMPTY_FILE",
                        "message": "Image file is empty",
                        "retryable": False,
                    },
                })
                continue

            try:
                image = Image.open(BytesIO(image_bytes))
                image.load()

            except (UnidentifiedImageError, OSError):

                processing_time = round(
                    (time.perf_counter() - start_time) * 1000,
                    2,
                )

                results.append({
                    "imageId": image_id,
                    "status": "FAILED",
                    "processingTimeMs": processing_time,
                    "embedding": None,
                    "ocr": [],
                    "error": {
                        "code": "IMAGE_CORRUPTED",
                        "message": "Cannot decode image stream with PIL",
                        "retryable": False,
                    },
                })
                continue

            if image.mode != "RGB":
                image = image.convert("RGB")

            result = image_processor.process(
                image=image,
                image_id=image_id,
            )

            processing_time = round(
                (time.perf_counter() - start_time) * 1000,
                2,
            )

            results.append({
                "imageId": image_id,
                "status": "SUCCESS",
                "processingTimeMs": processing_time,
                "embedding": result.get("embedding"),
                "ocr": result.get("ocr", []),
                "error": None,
            })

        except Exception as e:

            processing_time = round(
                (time.perf_counter() - start_time) * 1000,
                2,
            )

            results.append({
                "imageId": image_id,
                "status": "FAILED",
                "processingTimeMs": processing_time,
                "embedding": None,
                "ocr": [],
                "error": {
                    "code": "PROCESSING_ERROR",
                    "message": str(e),
                    "retryable": True,
                },
            })

    return {
        "results": results
    }