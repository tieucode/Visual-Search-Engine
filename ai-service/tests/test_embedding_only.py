import unittest
from unittest.mock import AsyncMock

from PIL import Image

from app.core.config import Settings
from app.core.runtime import Runtime


class EmbeddingOnlyTest(unittest.IsolatedAsyncioTestCase):
    async def test_background_embedding_batch_never_calls_ocr(self):
        runtime = Runtime(Settings())
        runtime.embed_images = AsyncMock(return_value=[[1.0], [2.0]])
        runtime.extract_ocr = AsyncMock(side_effect=AssertionError("OCR called"))
        try:
            with Image.new("RGB", (32, 32)) as frame:
                results = await runtime.process_batch([frame, frame], embedding_only=True)
            runtime.embed_images.assert_awaited_once_with([frame, frame], "background")
            runtime.extract_ocr.assert_not_awaited()
            self.assertEqual([item.embedding for item in results], [[1.0], [2.0]])
            self.assertEqual([item.ocr for item in results], [[], []])
        finally:
            await runtime.close()
