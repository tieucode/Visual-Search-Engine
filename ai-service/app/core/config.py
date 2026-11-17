from pathlib import Path
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):

    app_name: str = "Visual Search AI Service"
    app_version: str = "1.0.0"
    debug: bool = False

    siglip_model: str = "google/siglip2-base-patch16-224"

    # Vietnamese + English
    ocr_languages: str = "vi,en"

    device: Literal["cpu"] = "cpu"

    model_cache_dir: str = str(Path.home() / ".cache")
    torch_num_threads: int = Field(default=0, ge=0)
    torch_num_interop_threads: int = Field(default=1, gt=0)
    opencv_num_threads: int = Field(default=1, gt=0)
    embedding_microbatch_size: int = Field(default=2, ge=1, le=20)

    # Shared upload and execution limits
    max_upload_size_bytes: int = Field(default=15 * 1024 * 1024, gt=0)
    max_image_pixels: int = Field(default=25_000_000, gt=0)
    max_batch_images: int = Field(default=20, gt=0)
    max_active_processing: int = Field(default=3, gt=0)
    max_waiting_requests: int = Field(default=6, ge=0)
    search_reserved_waiting: int = Field(default=2, ge=0)
    max_queue_wait_seconds: int = Field(default=60, gt=0)

    @property
    def max_batch_upload_size_bytes(self) -> int:
        return self.max_batch_images * self.max_upload_size_bytes

    @property
    def max_in_flight_requests(self) -> int:
        return self.max_active_processing + self.max_waiting_requests

    model_config = SettingsConfigDict(
        env_file=".env", env_file_encoding="utf-8", case_sensitive=False, extra="ignore"
    )


settings = Settings()
