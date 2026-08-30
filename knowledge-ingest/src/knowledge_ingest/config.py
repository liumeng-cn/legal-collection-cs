"""配置：pydantic-settings 读取环境变量与 `.env`，镜像 Java 侧配置。"""

from __future__ import annotations

from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class ChunkStrategy(StrEnum):
    """分块策略。"""

    RECURSIVE = "recursive"
    SEMANTIC = "semantic"
    HYBRID = "hybrid"


class EmbeddingSettings(BaseSettings):
    """embedding 配置，镜像 Java `EmbeddingProperties`。"""

    model_config = SettingsConfigDict(
        env_prefix="EMBEDDING_", env_file=".env", env_file_encoding="utf-8", extra="ignore"
    )

    base_url: str = "https://dashscope.aliyuncs.com"
    api_key: str = ""
    model: str = "qwen3-vl-embedding"
    dimension: int = 1024
    batch_size: int = 100
    max_retries: int = 3
    timeout_seconds: float = 60.0


class DatabaseSettings(BaseSettings):
    """knowledge 库连接配置，镜像 Java `datasource.knowledge`。"""

    model_config = SettingsConfigDict(
        env_prefix="KNOWLEDGE_DATABASE_", env_file=".env", env_file_encoding="utf-8", extra="ignore"
    )

    url: str = "postgresql://postgres:postgres@localhost:5433/knowledge"


class ChunkSettings(BaseSettings):
    """分块配置。"""

    model_config = SettingsConfigDict(
        env_prefix="CHUNK_", env_file=".env", env_file_encoding="utf-8", extra="ignore"
    )

    strategy: ChunkStrategy = ChunkStrategy.HYBRID
    target_chars: int = 512
    overlap_chars: int = 64
    min_chars: int = 128
    max_chars: int = 1024


class Settings(BaseModel):
    """聚合配置。"""

    model_config = ConfigDict(frozen=True)

    embedding: EmbeddingSettings = Field(default_factory=EmbeddingSettings)
    database: DatabaseSettings = Field(default_factory=DatabaseSettings)
    chunk: ChunkSettings = Field(default_factory=ChunkSettings)
