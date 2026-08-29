package com.legalcs.service.rag;

import com.legalcs.config.EmbeddingProperties;
import com.legalcs.config.RerankProperties;
import com.legalcs.entity.RagChunk;
import com.legalcs.dto.RerankInput;
import com.legalcs.dto.RerankParameters;
import com.legalcs.dto.RerankRequest;
import com.legalcs.dto.RerankResponse;
import com.legalcs.dto.RerankResult;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeReranker implements Reranker {

    private static final String RERANK_PATH = "/api/v1/services/rerank/text-rerank/text-rerank";

    private final RestClient rerankRestClient;
    private final RerankProperties rerankProperties;
    private final EmbeddingProperties embeddingProperties;

    @Override
    public List<RagChunk> rerank(String query, List<RagChunk> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        try {
            return doRerank(query, candidates, topK);
        } catch (RuntimeException e) {
            log.warn("精排失败，退回融合排序: {}", e.getMessage());
            return candidates.stream().limit(topK).toList();
        }
    }

    private List<RagChunk> doRerank(String query, List<RagChunk> candidates, int topK) {
        List<String> documents = candidates.stream().map(RagChunk::getText).toList();
        RerankRequest request = new RerankRequest(
                rerankProperties.getModel(),
                new RerankInput(query, documents),
                new RerankParameters(topK, false));
        log.info("精排请求: {}", request);
        RerankResponse response = rerankRestClient.post()
                .uri(RERANK_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + embeddingProperties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(RerankResponse.class);
        if (response == null || response.output() == null || response.output().results() == null) {
            throw new IllegalStateException("精排接口返回为空");
        }
        log.info("精排响应: {}", response);
        List<RagChunk> result = new ArrayList<>();
        for (RerankResult item : response.output().results()) {
            if (item.index() != null && item.index() >= 0 && item.index() < candidates.size()) {
                RagChunk chunk = candidates.get(item.index());
                if (item.relevanceScore() != null) {
                    chunk = chunk.withScore(item.relevanceScore());
                }
                result.add(chunk);
            }
        }
        return result.isEmpty() ? candidates.stream().limit(topK).toList() : result;
    }
}
