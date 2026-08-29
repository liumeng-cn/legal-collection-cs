package com.legalcs.dto;

import java.util.List;

public record ChatCompletionResponse(List<ChatChoice> choices) {
}
