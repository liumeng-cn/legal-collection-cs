"""psycopg3 + pgvector 事务幂等写入。"""

from __future__ import annotations

import psycopg
from pgvector.psycopg import register_vector

from knowledge_ingest.config import DatabaseSettings
from knowledge_ingest.models import Chunk


class PostgresChunkWriter:
    """BEGIN → DELETE → 批量 INSERT → COMMIT，异常回滚。"""

    _DELETE_SQL = "DELETE FROM document_chunk WHERE document_id = %s"
    _INSERT_SQL = (
        "INSERT INTO document_chunk (document_id, chunk_index, chunk_text, embedding) "
        "VALUES (%s, %s, %s, %s)"
    )

    def __init__(self, settings: DatabaseSettings) -> None:
        self._settings = settings

    def write(self, document_id: int, chunks: list[Chunk], embeddings: list[list[float]]) -> int:
        if len(chunks) != len(embeddings):
            raise ValueError(f"chunk 数 {len(chunks)} 与 embedding 数 {len(embeddings)} 不一致")
        if not chunks:
            return 0

        rows = [
            (document_id, chunk.index, chunk.text, embedding)
            for chunk, embedding in zip(chunks, embeddings, strict=True)
        ]
        with psycopg.connect(self._settings.url) as conn:
            register_vector(conn)
            with conn.transaction(), conn.cursor() as cur:
                cur.execute(self._DELETE_SQL, (document_id,))
                cur.executemany(self._INSERT_SQL, rows)
        return len(chunks)
