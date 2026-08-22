package com.legalcs.aiops.web;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public record AiopsStream(
        long conversationId,
        Flux<ServerSentEvent<String>> events) {
}
