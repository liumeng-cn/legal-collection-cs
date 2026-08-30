package com.legalcs.dto;

public record EmbeddingRequest(
        String model,
        EmbeddingInput input,
        EmbeddingParameters parameters) {
}
