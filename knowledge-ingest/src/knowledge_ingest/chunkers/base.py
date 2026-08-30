"""Chunker 协议与共享工具。"""

from __future__ import annotations

from typing import Protocol

from knowledge_ingest.models import Chunk


class Chunker(Protocol):
    """分块器协议。"""

    def chunk(self, text: str) -> list[Chunk]:
        """将文本切分为块。"""
        ...


def merge_short_chunks(chunks: list[str], min_chars: int) -> list[str]:
    """将长度小于 `min_chars` 的块合并到前一相邻块。"""
    result: list[str] = []
    for text in chunks:
        if result and len(text) < min_chars:
            result[-1] += text
        else:
            result.append(text)
    if len(result) > 1 and len(result[-1]) < min_chars:
        result[-2] += result[-1]
        result.pop()
    return result
