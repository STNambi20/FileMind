from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = Field(default="FileMind AI Service", validation_alias="FILEMIND_AI_SERVICE_NAME")
    environment: str = Field(default="local", validation_alias="FILEMIND_AI_ENVIRONMENT")
    provider: str = Field(default="heuristic", validation_alias="FILEMIND_AI_PROVIDER")

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


settings = Settings()
