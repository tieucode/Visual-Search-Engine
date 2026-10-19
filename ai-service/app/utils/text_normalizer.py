import re
import unicodedata


def normalize_text(text: str) -> str:
    """Lowercase text, remove diacritics, and collapse consecutive whitespace."""
    if not text:
        return ""

    normalized = unicodedata.normalize("NFD", text.lower().strip())
    normalized = "".join(
        character
        for character in normalized
        if unicodedata.category(character) != "Mn"
    )
    normalized = normalized.replace("đ", "d")
    return re.sub(r"\s+", " ", normalized).strip()
