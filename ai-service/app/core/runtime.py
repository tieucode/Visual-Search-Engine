import asyncio
import logging
from typing import TYPE_CHECKING, Awaitable, Callable, TypeVar

from PIL import Image

from app.core.config import Settings
from app.core.cpu import configure_cpu
from app.core.errors import ServiceError
from app.core.execution import ExecutionStage, Priority, PriorityGate, settle, unavailable
from app.services.image_preprocessing import preprocess_image
from app.services.image_processor import ImageProcessor
from app.services.types import OCRResult, ProcessedImage

if TYPE_CHECKING:
    from app.services.embedding_service import EmbeddingService
    from app.services.ocr_service import OCRService

T = TypeVar("T")
logger = logging.getLogger(__name__)


class Runtime:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.ready = False
        self._embedding: EmbeddingService | None = None
        self._ocr: OCRService | None = None
        self._processor: ImageProcessor | None = None
        self._tasks: set[asyncio.Task] = set()
        self.admission = PriorityGate(
            settings.max_active_processing, settings.max_waiting_requests,
            settings.max_queue_wait_seconds,
            reserved_active=1,
            reserved_waiting=min(settings.search_reserved_waiting, settings.max_waiting_requests),
        )
        self.stages = {
            name: ExecutionStage(name, settings.max_in_flight_requests, settings.max_queue_wait_seconds)
            for name in ("decode", "embedding", "ocr")
        }

    def start(self) -> None:
        configure_cpu(self.settings)
        from app.services.embedding_service import EmbeddingService
        from app.services.ocr_service import OCRService

        self._embedding = EmbeddingService(self.settings)
        self._ocr = OCRService(self.settings)
        self._processor = ImageProcessor(self._embedding, self._ocr)
        with Image.new("RGB", (224, 224), "white") as image:
            self._processor.process(image)
        self._embedding.embed_text("warmup")
        self.ready = True
        logger.info("AI models initialized and warmed up")

    @property
    def embedding(self) -> "EmbeddingService":
        if self._embedding is None:
            raise unavailable("NOT_READY")
        return self._embedding

    @property
    def ocr(self) -> "OCRService":
        if self._ocr is None:
            raise unavailable("NOT_READY")
        return self._ocr

    @property
    def processor(self) -> ImageProcessor:
        if self._processor is None:
            raise unavailable("NOT_READY")
        return self._processor

    async def run(self, priority: Priority, operation: Callable[[], Awaitable[T]]) -> T:
        if not self.ready:
            raise unavailable("NOT_READY")

        async def admitted() -> T:
            async with self.admission.slot(priority):
                return await operation()

        task = asyncio.create_task(admitted())
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)
        try:
            return await asyncio.shield(task)
        except asyncio.CancelledError:
            task.cancel()
            await settle(task)
            raise

    async def decode(
        self, data: bytes, *, priority: Priority, resize: bool,
    ) -> Image.Image:
        return await self.stages["decode"].run(
            priority, preprocess_image, data, self.settings, resize=resize,
            discard=lambda image: image.close(),
        )

    async def embed_image(self, image: Image.Image, priority: Priority) -> list[float]:
        return await self.stages["embedding"].run(priority, self.embedding.embed_image, image)

    async def embed_text(self, text: str, priority: Priority) -> list[float]:
        return await self.stages["embedding"].run(priority, self.embedding.embed_text, text)

    async def embed_images(self, images: list[Image.Image], priority: Priority) -> list[list[float]]:
        return await self.stages["embedding"].run(priority, self.embedding.embed_images, images)

    async def extract_ocr(self, image: Image.Image, priority: Priority) -> OCRResult:
        return await self.stages["ocr"].run(priority, self.ocr.process, image)

    async def process(self, image: Image.Image, priority: Priority) -> ProcessedImage:
        embedding = await self.embed_image(image, priority)
        ocr = await self.extract_ocr(image, priority)
        return ProcessedImage(embedding, self.processor.indexed_ocr(ocr))

    async def process_batch(self, images: list[Image.Image]) -> list[ProcessedImage | Exception]:
        if not images:
            return []
        vectors: list[list[float] | Exception]
        try:
            vectors = list(await self.embed_images(images, "background"))
            if len(vectors) != len(images):
                raise RuntimeError("Embedding count does not match image count")
        except ServiceError:
            raise
        except Exception:
            logger.warning("Microbatch embedding failed; retrying individual images")
            vectors = []
            for image in images:
                try:
                    vectors.append(await self.embed_image(image, "background"))
                except Exception as exc:
                    vectors.append(exc)

        results: list[ProcessedImage | Exception] = []
        for image, vector in zip(images, vectors):
            if isinstance(vector, Exception):
                results.append(vector)
                continue
            try:
                ocr = await self.extract_ocr(image, "background")
                results.append(ProcessedImage(vector, self.processor.indexed_ocr(ocr)))
            except Exception as exc:
                results.append(exc)
        return results

    async def close(self) -> None:
        self.ready = False
        self.admission.close()
        for stage in self.stages.values():
            stage.stop()
        pending = list(self._tasks)
        for task in pending:
            task.cancel()
        if pending:
            await settle(asyncio.gather(*pending, return_exceptions=True))
        for stage in self.stages.values():
            stage.close()
        self._processor = None
        self._embedding = None
        self._ocr = None
