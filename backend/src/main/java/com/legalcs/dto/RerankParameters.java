package com.legalcs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RerankParameters(
        @JsonProperty("top_n") Integer topN,
        @JsonProperty("return_documents") Boolean returnDocuments) {
}
