"""领域模型：loader 输出的文档与分块结果。"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class LoadedDocument(BaseModel):
    """loader 加载后的文档。"""

    model_config = ConfigDict(frozen=True)

    title: str
    content: str
    allowed_roles: list[str] = Field(default_factory=list)
    case_id: int | None = None
    id: int | None = None


class Chunk(BaseModel):
    """文档分块。"""

    model_config = ConfigDict(frozen=True)

    text: str
    index: int
