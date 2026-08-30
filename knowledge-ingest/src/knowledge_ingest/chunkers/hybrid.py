"""Hybrid 分块：递归先切结构段，段内超长再语义精修。"""

from __future__ import annotations

from knowledge_ingest.chunkers.base import merge_short_chunks
from knowledge_ingest.chunkers.recursive import RecursiveSeparatorChunker
from knowledge_ingest.chunkers.semantic import SemanticChunker
from knowledge_ingest.config import ChunkSettings
from knowledge_ingest.embedders.base import Embedder
from knowledge_ingest.models import Chunk


class HybridChunker:
    """结构段 + 段内语义精修：仅对超长段内的句子调 embedding。"""

    def __init__(self, settings: ChunkSettings, embedder: Embedder) -> None:
        self._settings = settings
        self._recursive = RecursiveSeparatorChunker(settings)
        self._semantic = SemanticChunker(settings, embedder)

    def chunk(self, text: str) -> list[Chunk]:
        segments = self._recursive.split_structural(text)
        refined: list[str] = []
        for segment in segments:
            if len(segment) > self._settings.target_chars:
                refined.extend(chunk.text for chunk in self._semantic.chunk(segment))
            else:
                refined.append(segment)
        refined = merge_short_chunks(refined, self._settings.min_chars)
        return [Chunk(text=chunk, index=i) for i, chunk in enumerate(refined)]
