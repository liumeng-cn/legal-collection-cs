"""命令行入口：`ingest` / `list-docs` 子命令。"""

from __future__ import annotations

import argparse
import logging

from knowledge_ingest.chunkers.base import Chunker
from knowledge_ingest.chunkers.hybrid import HybridChunker
from knowledge_ingest.chunkers.recursive import RecursiveSeparatorChunker
from knowledge_ingest.chunkers.semantic import SemanticChunker
from knowledge_ingest.config import ChunkSettings, ChunkStrategy, Settings
from knowledge_ingest.embedders.base import Embedder
from knowledge_ingest.embedders.dashscope import DashScopeEmbedder
from knowledge_ingest.loaders.base import Loader
from knowledge_ingest.loaders.database import DatabaseDocumentLoader
from knowledge_ingest.loaders.text_file import TextFileLoader
from knowledge_ingest.pipeline import IngestionPipeline
from knowledge_ingest.writers.postgres import PostgresChunkWriter

logger = logging.getLogger(__name__)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="knowledge-ingest", description="法催平台知识库离线入库管道"
    )
    parser.add_argument("--verbose", action="store_true", help="输出 debug 日志")
    subparsers = parser.add_subparsers(dest="command", required=True)

    ingest = subparsers.add_parser("ingest", help="执行离线入库")
    ingest.add_argument(
        "--strategy",
        choices=[s.value for s in ChunkStrategy],
        default=None,
        help="分块策略（默认读取 CHUNK_STRATEGY）",
    )
    ingest.add_argument("--document-id", type=int, default=None, help="仅入库指定 document_id")
    ingest.add_argument("--source", choices=["database", "text"], default="database", help="输入源")
    ingest.add_argument("--text-dir", default=".", help="text 源扫描目录")

    subparsers.add_parser("list-docs", help="列出 knowledge 库中的文档")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    settings = Settings()

    if args.command == "list-docs":
        _list_documents(settings)
        return 0
    return _run_ingest(args, settings)


def _list_documents(settings: Settings) -> None:
    for document in DatabaseDocumentLoader(settings.database).load():
        print(f"{document.id}\t{document.title}")


def _run_ingest(args: argparse.Namespace, settings: Settings) -> int:
    strategy = ChunkStrategy(args.strategy) if args.strategy else settings.chunk.strategy
    embedder = DashScopeEmbedder(settings.embedding)
    chunker = _build_chunker(strategy, settings.chunk, embedder)
    loader = _build_loader(args, settings)
    writer = PostgresChunkWriter(settings.database)

    pipeline = IngestionPipeline(loader=loader, chunker=chunker, embedder=embedder, writer=writer)
    counts = pipeline.run()
    logger.info("入库完成：%d 篇文档，共 %d 个 chunk", len(counts), sum(counts))
    return 0


def _build_loader(args: argparse.Namespace, settings: Settings) -> Loader:
    if args.source == "database":
        return DatabaseDocumentLoader(settings.database, document_id=args.document_id)
    return TextFileLoader(args.text_dir)


def _build_chunker(
    strategy: ChunkStrategy, chunk_settings: ChunkSettings, embedder: Embedder
) -> Chunker:
    if strategy is ChunkStrategy.RECURSIVE:
        return RecursiveSeparatorChunker(chunk_settings)
    if strategy is ChunkStrategy.SEMANTIC:
        return SemanticChunker(chunk_settings, embedder)
    return HybridChunker(chunk_settings, embedder)
