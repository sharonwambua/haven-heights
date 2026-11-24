from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "sqlite:///./haven.db"
    secret_key: str = "dev-only-change-me-use-a-long-random-string-in-production"
    token_minutes: int = 60 * 24 * 14
    upload_dir: str = "uploads"
    water_rate_per_unit: float = 150.0   # KES per unit (m3)
    large_jump_units: float = 30.0       # readings above this need confirmation
    max_photo_mb: int = 10
    meter_model_dir: str = "models/meter_reader"
    bill_due_days: int = 7               # water bills fall due this many days after the reading
    firebase_credentials_file: str | None = None  # path to a Firebase service-account JSON

    # The first landlord account is created on startup if none exists.
    landlord_name: str = "Haven Heights Admin"
    landlord_phone: str = "0700000000"
    landlord_password: str = "changeme123"


settings = Settings()
