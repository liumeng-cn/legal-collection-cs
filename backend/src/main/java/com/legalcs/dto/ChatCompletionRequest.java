package com.legalcs.dto;

import java.util.List;

public record ChatCompletionRequest(String model, List<CompletionMessage> messages, Double temperature) {
}
