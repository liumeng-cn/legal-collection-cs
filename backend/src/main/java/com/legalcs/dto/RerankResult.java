package com.legalcs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RerankResult(Integer index, @JsonProperty("relevance_score") Double relevanceScore) {
}
