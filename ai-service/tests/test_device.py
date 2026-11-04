import unittest
from unittest.mock import patch

from pydantic import ValidationError

from app.core.config import Settings
from app.core.runtime import Runtime


class DeviceTests(unittest.TestCase):
    def test_cpu_and_cuda_are_supported(self):
        self.assertEqual(Settings(device="cpu").device, "cpu")
        self.assertEqual(Settings(device="cuda").device, "cuda")

    def test_invalid_device_is_rejected(self):
        with self.assertRaises(ValidationError):
            Settings(device="invalid")

    def test_cuda_unavailable_stops_before_loading_models(self):
        runtime = Runtime(Settings(device="cuda"))
        try:
            with patch("app.core.runtime.configure_cpu"), patch("torch.cuda.is_available", return_value=False):
                with self.assertRaisesRegex(RuntimeError, "CUDA-enabled PyTorch"):
                    runtime.start()
            self.assertFalse(runtime.ready)
            self.assertIsNone(runtime._embedding)
        finally:
            for stage in runtime.stages.values():
                stage.close()


if __name__ == "__main__":
    unittest.main()
