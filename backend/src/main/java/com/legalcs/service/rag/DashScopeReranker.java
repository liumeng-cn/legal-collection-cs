package com.legalcs.service.rag;

import com.legalcs.client.DashScopeClient;
import com.legalcs.dto.RerankResult;
import com.legalcs.entity.RagChunk;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeReranker implements Reranker {

    private final DashScopeClient dashScopeClient;

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
        List<RerankResult> results = dashScopeClient.rerank(query, documents, topK);
        List<RagChunk> result = new ArrayList<>();
        for (RerankResult item : results) {
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
