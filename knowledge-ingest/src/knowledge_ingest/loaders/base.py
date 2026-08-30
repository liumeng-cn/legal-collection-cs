"""Loader 协议。"""

from __future__ import annotations

from typing import Protocol

from knowledge_ingest.models import LoadedDocument


class Loader(Protocol):
    """文档加载器协议。"""

    def load(self) -> list[LoadedDocument]:
        """加载全部文档。"""
        ...
