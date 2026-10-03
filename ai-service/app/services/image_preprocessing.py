from io import BytesIO
from typing import Tuple

from PIL import Image, UnidentifiedImageError

# Các định dạng ảnh được hỗ trợ
SUPPORTED_FORMATS = {
    "JPEG",
    "PNG",
    "WEBP",
    "BMP",
    "GIF",
}


# Kích thước ảnh tối đa cho phép
MAX_IMAGE_WIDTH = 10000
MAX_IMAGE_HEIGHT = 10000


def load_image(image_bytes: bytes) -> Image.Image:
    if not image_bytes:
        raise ValueError("Dữ liệu ảnh không được để trống")

    try:
        image = Image.open(BytesIO(image_bytes))

        # Đảm bảo PIL đọc toàn bộ dữ liệu ảnh
        image.load()

    except UnidentifiedImageError:
        raise ValueError("File không phải là ảnh hợp lệ")

    except Exception as e:
        raise ValueError(f"Không thể đọc ảnh: {str(e)}")

    validate_image(image)

    # SigLIP và OCR nên làm việc với RGB
    image = image.convert("RGB")

    return image


def validate_image(image: Image.Image) -> None:
    # Kiểm tra format
    if image.format not in SUPPORTED_FORMATS:
        raise ValueError(f"Định dạng ảnh không được hỗ trợ: {image.format}")

    # Kiểm tra kích thước
    width, height = image.size

    if width <= 0 or height <= 0:
        raise ValueError("Kích thước ảnh không hợp lệ")

    if width > MAX_IMAGE_WIDTH or height > MAX_IMAGE_HEIGHT:
        raise ValueError(
            f"Kích thước ảnh quá lớn: {width}x{height}. "
            f"Tối đa {MAX_IMAGE_WIDTH}x{MAX_IMAGE_HEIGHT}"
        )


def resize_if_needed(
    image: Image.Image,
    max_size: Tuple[int, int] = (4096, 4096),
) -> Image.Image:
    max_width, max_height = max_size
    width, height = image.size

    # Nếu ảnh đã đủ nhỏ thì không cần resize
    if width <= max_width and height <= max_height:
        return image

    # copy để không thay đổi object ảnh ban đầu
    resized_image = image.copy()

    resized_image.thumbnail(
        (max_width, max_height),
        Image.Resampling.LANCZOS,
    )

    return resized_image


def preprocess_image(
    image_bytes: bytes,
    resize: bool = True,
) -> Image.Image:

    # 1. Load + validate + convert RGB
    image = load_image(image_bytes)

    # 2. Resize nếu cần
    if resize:
        image = resize_if_needed(image)

    return image
