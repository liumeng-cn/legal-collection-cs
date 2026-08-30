package com.legalcs.dto;

import java.util.List;

public record MemorySummaryResult(
        List<String> semantic,
        List<String> episodic) {
}
