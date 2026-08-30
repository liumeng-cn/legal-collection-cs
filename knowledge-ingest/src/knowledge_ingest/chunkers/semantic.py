"""语义分块器：批量 embedding + 相邻句余弦距离 + 自适应阈值 + 贪心次优切。"""

from __future__ import annotations

import math
import re

from knowledge_ingest.config import ChunkSettings
from knowledge_ingest.embedders.base import Embedder
from knowledge_ingest.models import Chunk


class SemanticChunker:
    """在相邻句语义相似度显著下降处断句。"""

    _SENTENCE_PATTERN = re.compile(r"(?<=[。！？；\n])")
    _THRESHOLD_PERCENTILE = 90

    def __init__(self, settings: ChunkSettings, embedder: Embedder) -> None:
        self._min = settings.min_chars
        self._max = settings.max_chars
        self._embedder = embedder

    def chunk(self, text: str) -> list[Chunk]:
        normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        sentences = [s.strip() for s in self._SENTENCE_PATTERN.split(normalized) if s.strip()]
        if not sentences:
            return []
        if len(sentences) == 1:
            return [Chunk(text=sentences[0], index=0)]

        embeddings = self._embedder.embed(sentences)
        if len(embeddings) != len(sentences):
            raise ValueError(f"embedding 条数 {len(embeddings)} 与句子数 {len(sentences)} 不一致")

        distances = self._adjacent_distances(embeddings)
        threshold = self._percentile(distances, self._THRESHOLD_PERCENTILE)
        groups = self._segment(sentences, distances, threshold)
        groups = self._enforce_max(sentences, distances, groups)

        chunks = ["".join(sentences[i] for i in group) for group in groups]
        return [Chunk(text=chunk, index=i) for i, chunk in enumerate(chunks)]

    def _adjacent_distances(self, embeddings: list[list[float]]) -> list[float]:
        distances: list[float] = []
        for prev, curr in zip(embeddings, embeddings[1:], strict=False):
            distances.append(1.0 - self._cosine(prev, curr))
        return distances

    def _cosine(self, a: list[float], b: list[float]) -> float:
        dot = sum(x * y for x, y in zip(a, b, strict=True))
        norm_a = math.sqrt(sum(x * x for x in a))
        norm_b = math.sqrt(sum(y * y for y in b))
        if norm_a == 0.0 or norm_b == 0.0:
            return 0.0
        return dot / (norm_a * norm_b)

    def _percentile(self, values: list[float], percentile: float) -> float:
        if not values:
            return 0.0
        ordered = sorted(values)
        rank = (len(ordered) - 1) * percentile / 100.0
        lower = math.floor(rank)
        upper = math.ceil(rank)
        if lower == upper:
            return ordered[int(rank)]
        weight_upper = rank - lower
        return ordered[lower] * (1.0 - weight_upper) + ordered[upper] * weight_upper

    def _segment(
        self, sentences: list[str], distances: list[float], threshold: float
    ) -> list[list[int]]:
        groups: list[list[int]] = []
        current: list[int] = [0]
        running = len(sentences[0])
        for i in range(len(sentences) - 1):
            if distances[i] > threshold and running >= self._min:
                groups.append(current)
                current = [i + 1]
                running = len(sentences[i + 1])
            else:
                current.append(i + 1)
                running += len(sentences[i + 1])
        groups.append(current)
        return groups

    def _enforce_max(
        self, sentences: list[str], distances: list[float], groups: list[list[int]]
    ) -> list[list[int]]:
        result: list[list[int]] = []
        for group in groups:
            result.extend(self._split_group(sentences, distances, group))
        return result

    def _split_group(
        self, sentences: list[str], distances: list[float], group: list[int]
    ) -> list[list[int]]:
        total = sum(len(sentences[i]) for i in group)
        if total <= self._max or len(group) <= 1:
            return [group]
        split = max(range(len(group) - 1), key=lambda j: distances[group[j]])
        left = group[: split + 1]
        right = group[split + 1 :]
        return self._split_group(sentences, distances, left) + self._split_group(
            sentences, distances, right
        )
