package com.legalcs.service.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.legalcs.config.ModelProperties;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class LlmQueryRewriterTest {

    @Test
    void rewriteFallsBackToOriginalOnFailure() {
        RestClient restClient = mock(RestClient.class);
        when(restClient.post()).thenThrow(new RuntimeException("down"));
        LlmQueryRewriter rewriter = new LlmQueryRewriter(restClient, mock(ModelProperties.class));

        assertEquals(List.of("原始问题"), rewriter.rewrite("原始问题"));
    }
}
