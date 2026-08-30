"""扫描目录纯文本文件，文件名作标题。"""

from __future__ import annotations

from pathlib import Path

from knowledge_ingest.models import LoadedDocument


class TextFileLoader:
    """扫描 `directory` 下的 `*.txt`，`title = 文件名`。"""

    def __init__(self, directory: str | Path) -> None:
        self._directory = Path(directory)

    def load(self) -> list[LoadedDocument]:
        documents: list[LoadedDocument] = []
        for path in sorted(self._directory.glob("*.txt")):
            content = path.read_text(encoding="utf-8")
            documents.append(LoadedDocument(title=path.stem, content=content))
        return documents
