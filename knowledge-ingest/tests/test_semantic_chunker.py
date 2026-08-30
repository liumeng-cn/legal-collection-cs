"""SemanticChunker 单元测试（注入假 embedder）。"""

from __future__ import annotations

from knowledge_ingest.chunkers.semantic import SemanticChunker
from knowledge_ingest.config import ChunkSettings


class _FakeEmbedder:
    def __init__(self, vectors: list[list[float]]) -> None:
        self._vectors = vectors
        self.calls: list[list[str]] = []

    def embed(self, texts: list[str]) -> list[list[float]]:
        self.calls.append(texts)
        return self._vectors


def test_break_at_semantic_boundary() -> None:
    vectors = [[1.0, 0.0], [0.95, 0.0], [0.0, 1.0], [0.0, 0.95]]
    embedder = _FakeEmbedder(vectors)
    settings = ChunkSettings(min_chars=1, max_chars=1000)
    chunker = SemanticChunker(settings, embedder)

    chunks = chunker.chunk("甲。乙。丙。丁。")

    assert [chunk.text for chunk in chunks] == ["甲。乙。", "丙。丁。"]
    assert embedder.calls == [["甲。", "乙。", "丙。", "丁。"]]


def test_max_length_splits_oversized() -> None:
    embedder = _FakeEmbedder([[1.0, 0.0]] * 3)
    settings = ChunkSettings(min_chars=1, max_chars=6)
    chunker = SemanticChunker(settings, embedder)

    chunks = chunker.chunk("一二三四五。六七八九十。甲乙丙丁戊。")

    assert len(chunks) == 3
    assert all(len(chunk.text) <= settings.max_chars for chunk in chunks)
