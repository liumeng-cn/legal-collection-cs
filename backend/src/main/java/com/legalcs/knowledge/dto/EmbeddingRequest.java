package com.legalcs.knowledge.dto;

public record EmbeddingRequest(
        String model,
        String input,
        Integer dimensions) {
}
