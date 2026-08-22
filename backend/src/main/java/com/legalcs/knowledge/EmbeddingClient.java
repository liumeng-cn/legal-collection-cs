package com.legalcs.knowledge;

import com.legalcs.config.EmbeddingProperties;
import com.legalcs.knowledge.dto.EmbeddingRequest;
import com.legalcs.knowledge.dto.EmbeddingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class EmbeddingClient {

    private final RestClient embeddingRestClient;
    private final EmbeddingProperties properties;

    public float[] embed(String text) {
        EmbeddingResponse response = embeddingRestClient.post()
                .uri("/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbeddingRequest(properties.getModel(), text, properties.getDimension()))
                .retrieve()
                .body(EmbeddingResponse.class);
        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("Embedding 接口返回为空");
        }
        return response.data().get(0).embedding();
    }
}
