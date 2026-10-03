from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):

    app_name: str = "Visual Search AI Service"
    app_version: str = "1.0.0"
    debug: bool = False

    siglip_model: str = "google/siglip-base-patch16-224"

    # Vietnamese + English
    ocr_languages: str = "vi,en"

    # cpu / cuda
    device: str = "cpu"

    model_cache_dir: str = "./models"

    model_config = SettingsConfigDict(
        env_file=".env", env_file_encoding="utf-8", case_sensitive=False, extra="ignore"
    )


settings = Settings()
