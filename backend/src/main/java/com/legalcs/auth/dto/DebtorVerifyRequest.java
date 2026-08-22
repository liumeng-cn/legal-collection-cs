package com.legalcs.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record DebtorVerifyRequest(
        @NotBlank String identifier) {
}
