package com.legalcs.service.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.legalcs.config.EmbeddingProperties;
import com.legalcs.config.RerankProperties;
import com.legalcs.entity.RagChunk;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class DashScopeRerankerTest {

    @Test
    void rerankFallsBackToRrfOrderOnFailure() {
        RestClient restClient = mock(RestClient.class);
        when(restClient.post()).thenThrow(new RuntimeException("down"));
        DashScopeReranker reranker = new DashScopeReranker(
                restClient, mock(RerankProperties.class), mock(EmbeddingProperties.class));

        RagChunk a = new RagChunk(1L, 1L, 0, "A", "a", 0.9);
        RagChunk b = new RagChunk(2L, 2L, 0, "B", "b", 0.8);
        RagChunk c = new RagChunk(3L, 3L, 0, "C", "c", 0.7);

        assertEquals(List.of(a, b), reranker.rerank("q", List.of(a, b, c), 2));
    }
}
