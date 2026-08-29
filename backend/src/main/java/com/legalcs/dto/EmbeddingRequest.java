package com.legalcs.dto;

public record EmbeddingRequest(
        String model,
        String input,
        Integer dimensions) {
}
