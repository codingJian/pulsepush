import os
from functools import lru_cache


class Settings:
    def __init__(self):
        self.app_name: str = os.getenv("APP_NAME", "Webhook Push")
        # Default: SQLite file next to backend dir; switch via DATABASE_URL
        # e.g. postgresql+asyncpg://user:pass@host:5432/webhook_push
        self.database_url: str = os.getenv(
            "DATABASE_URL", "sqlite+aiosqlite:///./webhook_push.db"
        )
        self.jwt_secret: str = os.getenv("JWT_SECRET", "dev-secret-change-me")
        self.jwt_algorithm: str = "HS256"
        self.jwt_expire_days: int = int(os.getenv("JWT_EXPIRE_DAYS", "7"))
        # Path to Firebase service-account JSON; empty / missing -> dry-run
        self.firebase_credentials: str = os.getenv("FIREBASE_CREDENTIALS", "")
        self.base_url: str = os.getenv("BASE_URL", "http://localhost:8000")
        self.rate_limit_per_minute: int = int(os.getenv("RATE_LIMIT_PER_MINUTE", "60"))


@lru_cache
def get_settings() -> Settings:
    return Settings()
