"""DashScope 原生多模态 embedding 客户端（qwen3-vl-embedding）。"""

from __future__ import annotations

import time

import httpx
from pydantic import BaseModel

from knowledge_ingest.config import EmbeddingSettings

_EMBEDDING_PATH = "/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding"
_TEXT_FACTOR = 1


class _EmbeddingItem(BaseModel):
    embedding: list[float]
    index: int


class _EmbeddingOutput(BaseModel):
    embeddings: list[_EmbeddingItem]


class _EmbeddingResponse(BaseModel):
    output: _EmbeddingOutput


class DashScopeEmbedder:
    """`qwen3-vl-embedding` 原生多模态接口：批量提交 + 拆批 + 重试退避 + 维度校验。"""

    def __init__(self, settings: EmbeddingSettings, client: httpx.Client | None = None) -> None:
        self._settings = settings
        self._client = client or httpx.Client(timeout=settings.timeout_seconds)

    def embed(self, texts: list[str]) -> list[list[float]]:
        results: list[list[float]] = []
        for batch in self._batches(texts):
            results.extend(self._embed_batch(batch))
        return results

    def _batches(self, texts: list[str]) -> list[list[str]]:
        size = self._settings.batch_size
        return [texts[i : i + size] for i in range(0, len(texts), size)]

    def _embed_batch(self, texts: list[str]) -> list[list[float]]:
        url = f"{self._settings.base_url.rstrip('/')}{_EMBEDDING_PATH}"
        headers = {"Authorization": f"Bearer {self._settings.api_key}"}
        payload = {
            "model": self._settings.model,
            "input": {"contents": [{"factor": _TEXT_FACTOR, "text": text} for text in texts]},
            "parameters": {"dimension": self._settings.dimension},
        }

        last_error: Exception | None = None
        for attempt in range(self._settings.max_retries):
            try:
                response = self._client.post(url, json=payload, headers=headers)
                response.raise_for_status()
                parsed = _EmbeddingResponse.model_validate(response.json())
                items = sorted(parsed.output.embeddings, key=lambda item: item.index)
                embeddings = [item.embedding for item in items]
                self._validate(embeddings, len(texts))
                return embeddings
            except (httpx.HTTPError, ValueError) as exc:
                last_error = exc
                if attempt < self._settings.max_retries - 1:
                    time.sleep(self._backoff(attempt))
        raise RuntimeError(f"embedding 调用失败：{last_error}") from last_error

    def _validate(self, embeddings: list[list[float]], expected: int) -> None:
        if len(embeddings) != expected:
            raise ValueError(f"embedding 返回条数 {len(embeddings)} 与输入 {expected} 不一致")
        for vector in embeddings:
            if len(vector) != self._settings.dimension:
                raise ValueError(
                    f"embedding 维度 {len(vector)} 与配置 {self._settings.dimension} 不一致"
                )

    @staticmethod
    def _backoff(attempt: int) -> float:
        return 2.0**attempt
