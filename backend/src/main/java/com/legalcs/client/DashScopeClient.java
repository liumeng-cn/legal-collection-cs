package com.legalcs.client;

import com.legalcs.config.DashScopeProperties;
import com.legalcs.dto.EmbeddingContent;
import com.legalcs.dto.EmbeddingInput;
import com.legalcs.dto.EmbeddingParameters;
import com.legalcs.dto.EmbeddingRequest;
import com.legalcs.dto.EmbeddingResponse;
import com.legalcs.dto.RerankInput;
import com.legalcs.dto.RerankParameters;
import com.legalcs.dto.RerankRequest;
import com.legalcs.dto.RerankResponse;
import com.legalcs.dto.RerankResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeClient {

    private static final String EMBEDDING_PATH =
            "/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding";
    private static final String RERANK_PATH =
            "/api/v1/services/rerank/text-rerank/text-rerank";
    private static final int TEXT_FACTOR = 1;

    private final RestClient dashScopeRestClient;
    private final DashScopeProperties properties;

    public float[] embed(String text) {
        try {
            return doEmbed(text);
        } catch (RuntimeException e) {
            return doEmbed(text);
        }
    }

    public List<RerankResult> rerank(String query, List<String> documents, int topK) {
        RerankRequest request = new RerankRequest(
                properties.rerank().model(),
                new RerankInput(query, documents),
                new RerankParameters(topK, false));
        log.info("精排请求: {}", request);
        RerankResponse response = dashScopeRestClient.post()
                .uri(RERANK_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(RerankResponse.class);
        if (response == null || response.output() == null || response.output().results() == null) {
            throw new IllegalStateException("精排接口返回为空");
        }
        log.info("精排响应: {}", response);
        return response.output().results();
    }

    private float[] doEmbed(String text) {
        EmbeddingRequest request = new EmbeddingRequest(
                properties.embedding().model(),
                new EmbeddingInput(List.of(new EmbeddingContent(TEXT_FACTOR, text))),
                new EmbeddingParameters(properties.embedding().dimension()));
        EmbeddingResponse response = dashScopeRestClient.post()
                .uri(EMBEDDING_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(EmbeddingResponse.class);
        if (response == null || response.output() == null
                || response.output().embeddings() == null
                || response.output().embeddings().isEmpty()) {
            throw new IllegalStateException("Embedding 接口返回为空");
        }
        return response.output().embeddings().get(0).embedding();
    }
}
