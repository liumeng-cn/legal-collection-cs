package com.legalcs.dto;

import java.util.List;

public record RerankInput(String query, List<String> documents) {
}
