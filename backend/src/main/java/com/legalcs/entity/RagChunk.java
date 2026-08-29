package com.legalcs.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class RagChunk {

    private final long id;
    private final long documentId;
    private final int chunkIndex;
    private final String title;
    private final String text;
    private final double score;

    public RagChunk withScore(double newScore) {
        return new RagChunk(id, documentId, chunkIndex, title, text, newScore);
    }
}
