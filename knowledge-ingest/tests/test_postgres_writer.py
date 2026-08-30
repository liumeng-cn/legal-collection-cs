"""PostgresChunkWriter 集成测试（需本地 knowledge 库）。"""

from __future__ import annotations

from collections.abc import Iterator
from typing import Any

import psycopg
import pytest

from knowledge_ingest.config import DatabaseSettings
from knowledge_ingest.models import Chunk
from knowledge_ingest.writers.postgres import PostgresChunkWriter

_DIMENSION = 1024


def _database_available() -> bool:
    try:
        with psycopg.connect(DatabaseSettings().url, connect_timeout=3) as conn:
            conn.execute("SELECT 1")
    except Exception:
        return False
    return True


pytestmark = pytest.mark.skipif(
    not _database_available(), reason="knowledge 库不可用，跳过集成测试"
)


@pytest.fixture
def db() -> Iterator[psycopg.Connection[tuple[Any, ...]]]:
    conn = psycopg.connect(DatabaseSettings().url, autocommit=True, connect_timeout=3)
    try:
        yield conn
    finally:
        conn.close()


@pytest.fixture
def document_id(db: psycopg.Connection[tuple[Any, ...]]) -> Iterator[int]:
    with db.cursor() as cur:
        cur.execute(
            "INSERT INTO document (title, content, allowed_roles, case_id) "
            "VALUES (%s, %s, %s, %s) RETURNING id",
            ("knowledge-ingest 测试文档", "测试内容", ["催员", "债务人"], None),
        )
        row = cur.fetchone()
        assert row is not None
        doc_id = int(row[0])
    yield doc_id
    with db.cursor() as cur:
        cur.execute("DELETE FROM document_chunk WHERE document_id = %s", (doc_id,))
        cur.execute("DELETE FROM document WHERE id = %s", (doc_id,))


def _count_chunks(db: psycopg.Connection[tuple[Any, ...]], document_id: int) -> int:
    with db.cursor() as cur:
        cur.execute("SELECT count(*) FROM document_chunk WHERE document_id = %s", (document_id,))
        row = cur.fetchone()
        assert row is not None
        return int(row[0])


def test_idempotent_rerun(db: psycopg.Connection[tuple[Any, ...]], document_id: int) -> None:
    writer = PostgresChunkWriter(DatabaseSettings())
    chunks = [Chunk(text="第一块", index=0), Chunk(text="第二块", index=1)]
    embeddings = [[1.0] * _DIMENSION, [2.0] * _DIMENSION]

    assert writer.write(document_id, chunks, embeddings) == 2
    assert writer.write(document_id, chunks, embeddings) == 2
    assert _count_chunks(db, document_id) == 2


def test_transaction_rollback(db: psycopg.Connection[tuple[Any, ...]], document_id: int) -> None:
    writer = PostgresChunkWriter(DatabaseSettings())
    writer.write(document_id, [Chunk(text="回滚前数据", index=0)], [[1.0] * _DIMENSION])

    with pytest.raises(psycopg.Error):
        writer.write(document_id, [Chunk(text="坏数据", index=0)], [[1.0, 2.0, 3.0]])

    assert _count_chunks(db, document_id) == 1
    with db.cursor() as cur:
        cur.execute("SELECT chunk_text FROM document_chunk WHERE document_id = %s", (document_id,))
        row = cur.fetchone()
        assert row is not None
        assert row[0] == "回滚前数据"
