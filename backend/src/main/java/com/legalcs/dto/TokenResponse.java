package com.legalcs.dto;

public record TokenResponse(
        String token,
        String role,
        String userId,
        String name) {
}
