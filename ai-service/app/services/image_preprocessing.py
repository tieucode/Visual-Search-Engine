from io import BytesIO

from PIL import Image, UnidentifiedImageError

from app.core.config import Settings
from app.core.errors import ServiceError

SUPPORTED_FORMATS = {"JPEG", "PNG", "WEBP", "BMP", "GIF"}
SUPPORTED_CONTENT_TYPES = {
    "image/jpeg", "image/png", "image/webp", "image/bmp", "image/gif",
}


def validate_upload(filename: str | None, content_type: str | None, *, batch: bool) -> None:
    if not filename:
        raise ServiceError("Tên file không hợp lệ", code="INVALID_FILENAME")
    generic_type = content_type in (None, "application/octet-stream")
    if content_type not in SUPPORTED_CONTENT_TYPES and not (batch and generic_type):
        raise ServiceError("Chỉ hỗ trợ JPG, PNG, WEBP, BMP, GIF.", code="UNSUPPORTED_FORMAT")


def preprocess_image(
    image_bytes: bytes, settings: Settings, *, resize: bool = True,
) -> Image.Image:
    if not image_bytes:
        raise ServiceError("File ảnh rỗng", code="EMPTY_FILE")
    if len(image_bytes) > settings.max_upload_size_bytes:
        raise ServiceError("Ảnh vượt quá giới hạn dung lượng", code="IMAGE_TOO_LARGE", status_code=413)

    try:
        with Image.open(BytesIO(image_bytes)) as source:
            if source.format not in SUPPORTED_FORMATS:
                raise ServiceError("Định dạng ảnh không được hỗ trợ", code="UNSUPPORTED_FORMAT")
            width, height = source.size
            if width <= 0 or height <= 0:
                raise ServiceError("Kích thước ảnh không hợp lệ", code="INVALID_DIMENSIONS")
            if width * height > settings.max_image_pixels:
                raise ServiceError("Ảnh vượt quá giới hạn số pixel", code="IMAGE_TOO_LARGE", status_code=413)
            if resize and (width > 10000 or height > 10000):
                raise ServiceError("Kích thước ảnh vượt quá 10000x10000", code="IMAGE_TOO_LARGE", status_code=413)
            source.load()
            image = source.convert("RGB")
    except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError) as exc:
        raise ServiceError("Không thể đọc file ảnh", code="IMAGE_CORRUPTED") from exc

    try:
        # Standalone embedding/OCR historically resize; combined/indexing retain input coordinates.
        if resize and (image.width > 4096 or image.height > 4096):
            image.thumbnail((4096, 4096), Image.Resampling.LANCZOS)
        return image
    except BaseException:
        image.close()
        raise
