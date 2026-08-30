"""RecursiveSeparatorChunker 单元测试。"""

from __future__ import annotations

from knowledge_ingest.chunkers.recursive import RecursiveSeparatorChunker
from knowledge_ingest.config import ChunkSettings


def test_split_structural_heading_priority() -> None:
    settings = ChunkSettings(target_chars=512, overlap_chars=64, min_chars=128, max_chars=1024)
    chunker = RecursiveSeparatorChunker(settings)
    text = "第一章 总则。这是第一句。\n## 第二条 定义\n这是第二条内容。"

    pieces = chunker.split_structural(text)

    assert pieces[0].startswith("第一章")
    assert any(piece.startswith("## 第二条") for piece in pieces)


def test_overlap_between_chunks() -> None:
    settings = ChunkSettings(target_chars=30, overlap_chars=10, min_chars=1, max_chars=100)
    chunker = RecursiveSeparatorChunker(settings)
    text = "一二三四五六七八九十。" * 10

    chunks = chunker.chunk(text)

    assert len(chunks) > 1
    for prev, curr in zip(chunks, chunks[1:], strict=False):
        assert curr.text.startswith(prev.text[-10:])


def test_no_loss_without_overlap() -> None:
    settings = ChunkSettings(target_chars=50, overlap_chars=0, min_chars=1, max_chars=60)
    chunker = RecursiveSeparatorChunker(settings)
    text = "这是一句很长的话没有标点一直延续下去" * 10

    chunks = chunker.chunk(text)

    assert "".join(chunk.text for chunk in chunks) == text
    assert all(len(chunk.text) <= settings.max_chars for chunk in chunks)
