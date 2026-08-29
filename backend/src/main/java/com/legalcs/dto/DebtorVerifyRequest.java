package com.legalcs.dto;

import jakarta.validation.constraints.NotBlank;

public record DebtorVerifyRequest(
        @NotBlank String identifier) {
}
