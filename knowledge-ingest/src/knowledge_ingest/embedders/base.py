"""Embedder 协议。"""

from __future__ import annotations

from typing import Protocol


class Embedder(Protocol):
    """embedding 生成器协议。"""

    def embed(self, texts: list[str]) -> list[list[float]]:
        """批量生成 embedding，返回与输入等长的向量列表。"""
        ...
