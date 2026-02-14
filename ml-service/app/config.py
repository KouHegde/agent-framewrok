"""
Application configuration loaded from environment variables.
"""

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Application settings from environment variables."""
    database_url: str = "postgresql://agent_user:agent_pass@localhost:5432/agent_framework"
    model_path: str = "./models"
    model_name: str = "tool-selector"
    min_samples_for_training: int = 100
    embedding_model: str = "sentence-transformers/all-MiniLM-L6-v2"

    class Config:
        env_prefix = ""


settings = Settings()
