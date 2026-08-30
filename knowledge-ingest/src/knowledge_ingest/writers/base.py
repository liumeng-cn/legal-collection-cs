"""Writer 协议。"""

from __future__ import annotations

from typing import Protocol

from knowledge_ingest.models import Chunk


class Writer(Protocol):
    """chunk 写入器协议。"""

    def write(self, document_id: int, chunks: list[Chunk], embeddings: list[list[float]]) -> int:
        """事务写入 chunk，返回写入条数。"""
        ...
