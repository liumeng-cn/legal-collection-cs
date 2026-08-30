"""从 knowledge 库 `document` 表加载文档。"""

from __future__ import annotations

import psycopg
from psycopg.rows import dict_row

from knowledge_ingest.config import DatabaseSettings
from knowledge_ingest.models import LoadedDocument


class DatabaseDocumentLoader:
    """从 `document` 表加载文档，可选 `--document-id` 单篇。"""

    _COLUMNS = "id, title, content, allowed_roles, case_id"

    def __init__(self, settings: DatabaseSettings, document_id: int | None = None) -> None:
        self._settings = settings
        self._document_id = document_id

    def load(self) -> list[LoadedDocument]:
        query = f"SELECT {self._COLUMNS} FROM document"
        params: tuple[int, ...] | None = None
        if self._document_id is not None:
            query += " WHERE id = %s"
            params = (self._document_id,)

        with (
            psycopg.connect(self._settings.url, row_factory=dict_row) as conn,
            conn.cursor() as cur,
        ):
            cur.execute(query, params)
            rows = cur.fetchall()

        return [
            LoadedDocument(
                id=row["id"],
                title=row["title"],
                content=row["content"],
                allowed_roles=row["allowed_roles"] or [],
                case_id=row["case_id"],
            )
            for row in rows
        ]
