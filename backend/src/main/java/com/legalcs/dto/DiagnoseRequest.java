package com.legalcs.dto;

import jakarta.validation.constraints.NotBlank;

public record DiagnoseRequest(
        String conversationId,
        @NotBlank String message) {
}
