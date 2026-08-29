package com.legalcs.service.rag;

import com.legalcs.service.auth.AuthContext;
import com.legalcs.entity.RagChunk;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagPipeline {

    private static final double RRF_K = 60.0;
    private static final int RECALL_TOP_K = 10;
    private static final int CANDIDATE_POOL_SIZE = 20;
    private static final int FINAL_TOP_K = 5;

    private final QueryRewriter queryRewriter;
    private final List<Retriever> retrievers;
    private final Reranker reranker;

    public List<RagChunk> retrieve(String query, AuthContext authContext) {
        Map<Long, RagChunk> chunksById = new LinkedHashMap<>();
        Map<Long, Double> scores = new LinkedHashMap<>();
        for (String subquery : queryRewriter.rewrite(query)) {
            for (Retriever retriever : retrievers) {
                List<RagChunk> result;
                try {
                    result = retriever.retrieve(subquery, authContext, RECALL_TOP_K);
                } catch (RuntimeException e) {
                    log.warn("召回器降级，跳过 {}: {}", retriever.getClass().getSimpleName(), e.getMessage());
                    continue;
                }
                for (int rank = 0; rank < result.size(); rank++) {
                    RagChunk chunk = result.get(rank);
                    chunksById.putIfAbsent(chunk.getId(), chunk);
                    scores.merge(chunk.getId(), 1.0 / (RRF_K + rank + 1), Double::sum);
                }
            }
        }
        List<RagChunk> candidates = scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(CANDIDATE_POOL_SIZE)
                .map(entry -> chunksById.get(entry.getKey()))
                .toList();
        return reranker.rerank(query, candidates, FINAL_TOP_K);
    }
}
