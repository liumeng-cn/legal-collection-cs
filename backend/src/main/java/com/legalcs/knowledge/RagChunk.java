package com.legalcs.knowledge;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class RagChunk {

    private final String title;
    private final String text;
    private final double score;
}
