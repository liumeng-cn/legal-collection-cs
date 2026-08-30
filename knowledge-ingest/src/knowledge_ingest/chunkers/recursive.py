"""递归分隔符分块器：标题 > 段落 > 句子，带重叠与 min/max 约束。"""

from __future__ import annotations

import re

from knowledge_ingest.chunkers.base import merge_short_chunks
from knowledge_ingest.config import ChunkSettings
from knowledge_ingest.models import Chunk


class RecursiveSeparatorChunker:
    """按分隔符优先级递归切分，合并到目标长度并加重叠。"""

    _SEPARATORS: tuple[str, ...] = (
        r"\n(?=#{1,6}\s)",  # Markdown 标题
        r"\n{2,}",  # 段落
        r"(?<=[。！？；])",  # 句子（保留标点）
    )

    def __init__(self, settings: ChunkSettings) -> None:
        self._target = settings.target_chars
        self._overlap = settings.overlap_chars
        self._min = settings.min_chars
        self._max = settings.max_chars

    def chunk(self, text: str) -> list[Chunk]:
        pieces = self.split_structural(text)
        chunks = self._overlap_chunks(self._merge(pieces))
        chunks = self._enforce_max(chunks)
        chunks = merge_short_chunks(chunks, self._min)
        return [Chunk(text=chunk, index=i) for i, chunk in enumerate(chunks)]

    def split_structural(self, text: str) -> list[str]:
        """仅按结构切分为片段（不合并到目标长度），供 hybrid 编排复用。"""
        normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        return self._recursive(normalized, 0)

    def _recursive(self, text: str, level: int) -> list[str]:
        if level >= len(self._SEPARATORS):
            return self._hard_split(text, self._target)
        parts = re.split(self._SEPARATORS[level], text)
        pieces: list[str] = []
        for part in parts:
            part = part.strip()
            if not part:
                continue
            if len(part) <= self._target:
                pieces.append(part)
            else:
                pieces.extend(self._recursive(part, level + 1))
        return pieces

    def _hard_split(self, text: str, size: int) -> list[str]:
        pieces: list[str] = []
        remaining = text
        while len(remaining) > size:
            pieces.append(remaining[:size].strip())
            remaining = remaining[size:]
        if remaining.strip():
            pieces.append(remaining.strip())
        return pieces

    def _merge(self, pieces: list[str]) -> list[str]:
        base: list[str] = []
        current = ""
        for piece in pieces:
            if not current:
                current = piece
            elif len(current) + len(piece) <= self._target:
                current += piece
            else:
                base.append(current)
                current = piece
        if current:
            base.append(current)
        return base

    def _overlap_chunks(self, base: list[str]) -> list[str]:
        if self._overlap <= 0 or len(base) <= 1:
            return base
        result: list[str] = []
        for i, chunk in enumerate(base):
            if i > 0:
                prev = base[i - 1]
                tail = prev[-self._overlap :] if len(prev) > self._overlap else prev
                chunk = tail + chunk
            result.append(chunk)
        return result

    def _enforce_max(self, chunks: list[str]) -> list[str]:
        result: list[str] = []
        for chunk in chunks:
            if len(chunk) <= self._max:
                result.append(chunk)
            else:
                result.extend(self._hard_split(chunk, self._max))
        return result
