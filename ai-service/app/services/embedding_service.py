import torch

from PIL import Image
from transformers import AutoProcessor, AutoModel

from app.core.config import settings


class EmbeddingService:
    def __init__(self):
        print("Loading SigLIP model...")

        # Device: CPU hoặc CUDA
        self.device = torch.device(settings.device)

        # Tên model lấy từ config.py
        self.model_name = settings.siglip_model

        # Load processor
        self.processor = AutoProcessor.from_pretrained(self.model_name)

        # Load model
        self.model = AutoModel.from_pretrained(self.model_name)

        # Đưa model lên CPU/GPU
        self.model.to(self.device)

        # Chuyển sang evaluation mode
        self.model.eval()

        print(f"SigLIP loaded successfully: {self.model_name}")
        print(f"Device: {self.device}")

    def embed_image(self, image: Image.Image) -> list[float]:
        if image is None:
            raise ValueError("Image không được để trống")

        # Đảm bảo ảnh RGB
        if image.mode != "RGB":
            image = image.convert("RGB")

        # Processor chuẩn bị input cho SigLIP
        inputs = self.processor(images=image, return_tensors="pt")

        # Đưa input lên cùng device với model
        inputs = {key: value.to(self.device) for key, value in inputs.items()}

        # Không tính gradient khi inference
        with torch.no_grad():
            outputs = self.model.get_image_features(**inputs)

            if hasattr(outputs, "pooler_output"):
                image_features = outputs.pooler_output
            else:
                image_features = outputs

        # Normalize vector
        image_features = torch.nn.functional.normalize(image_features, p=2, dim=-1)

        # Chuyển tensor -> list Python
        embedding = image_features[0].cpu().tolist()

        return embedding

    def embed_text(self, text: str) -> list[float]:
        if not text or not text.strip():
            raise ValueError("Text không được để trống")

        # Chuẩn bị input cho SigLIP
        inputs = self.processor(
            text=text,
            return_tensors="pt",
            padding="max_length"
        )

        # Đưa input lên cùng device với model
        inputs = {
            key: value.to(self.device)
            for key, value in inputs.items()
        }

        with torch.no_grad():
            output = self.model.get_text_features(**inputs)

        # Transformers phiên bản mới trả về
        # BaseModelOutputWithPooling
        if hasattr(output, "pooler_output"):
            text_features = output.pooler_output
        else:
            # Tương thích trường hợp phiên bản cũ
            text_features = output

        # Normalize vector
        text_features = torch.nn.functional.normalize(
            text_features,
            p=2,
            dim=-1
        )

        return text_features[0].cpu().tolist()


# Singleton service
embedding_service = EmbeddingService()
