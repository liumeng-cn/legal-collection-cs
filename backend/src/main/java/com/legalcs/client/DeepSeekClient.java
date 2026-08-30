package com.legalcs.client;

import com.legalcs.config.ModelProperties;
import com.legalcs.dto.ChatCompletionRequest;
import com.legalcs.dto.ChatCompletionResponse;
import com.legalcs.dto.CompletionMessage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final RestClient modelRestClient;
    private final ModelProperties properties;

    public String chat(List<CompletionMessage> messages, double temperature) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                properties.getName(), messages, temperature);
        log.info("LLM 请求: {}", request);
        ChatCompletionResponse response = modelRestClient.post()
                .uri(CHAT_COMPLETIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null) {
            throw new IllegalStateException("LLM 接口返回为空");
        }
        String content = response.choices().get(0).message().content();
        log.info("LLM 响应: {}", content);
        return content;
    }
}
