from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class OCRDetection:
    text: str
    confidence: float
    bounding_box: list[list[int | float]]


@dataclass(frozen=True, slots=True)
class OCRResult:
    text: str
    detections: list[OCRDetection]


@dataclass(frozen=True, slots=True)
class BoundingBox:
    x: float
    y: float
    width: float
    height: float


@dataclass(frozen=True, slots=True)
class IndexedOCR:
    text: str
    normalizedText: str
    confidence: float
    boundingBox: BoundingBox


@dataclass(frozen=True, slots=True)
class ProcessedImage:
    embedding: list[float]
    ocr: list[IndexedOCR]
