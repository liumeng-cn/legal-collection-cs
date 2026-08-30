"""入库管道编排：load → chunk → embed → write。"""

from __future__ import annotations

import logging

from knowledge_ingest.chunkers.base import Chunker
from knowledge_ingest.embedders.base import Embedder
from knowledge_ingest.loaders.base import Loader
from knowledge_ingest.models import LoadedDocument
from knowledge_ingest.writers.base import Writer

logger = logging.getLogger(__name__)


class IngestionPipeline:
    """加载文档 → 分块 → 批量 embedding → 事务写入。"""

    def __init__(
        self,
        loader: Loader,
        chunker: Chunker,
        embedder: Embedder,
        writer: Writer,
    ) -> None:
        self._loader = loader
        self._chunker = chunker
        self._embedder = embedder
        self._writer = writer

    def run(self) -> list[int]:
        return [self._process(document) for document in self._loader.load()]

    def _process(self, document: LoadedDocument) -> int:
        if document.id is None:
            logger.warning("文档「%s」无 id，跳过写入（txt 源需先入库 document）", document.title)
            return 0
        chunks = self._chunker.chunk(document.content)
        if not chunks:
            logger.warning("文档「%s」分块为空，跳过", document.title)
            return 0
        embeddings = self._embedder.embed([chunk.text for chunk in chunks])
        return self._writer.write(document.id, chunks, embeddings)
