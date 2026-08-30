package com.legalcs.service.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.legalcs.client.DeepSeekClient;
import java.util.List;

import org.junit.jupiter.api.Test;

class LlmQueryRewriterTest {

    @Test
    void rewriteFallsBackToOriginalOnFailure() {
        DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
        when(deepSeekClient.chat(anyList(), anyDouble())).thenThrow(new RuntimeException("down"));
        LlmQueryRewriter rewriter = new LlmQueryRewriter(deepSeekClient);

        assertEquals(List.of("原始问题"), rewriter.rewrite("原始问题"));
    }
}
