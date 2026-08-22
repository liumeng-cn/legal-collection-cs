package com.legalcs.chat;

import reactor.core.publisher.Flux;

public record ChatStream(
        long conversationId,
        Flux<String> deltas) {
}
