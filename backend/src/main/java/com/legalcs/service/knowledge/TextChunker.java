package com.legalcs.service.knowledge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {

    private static final String SENTENCE_SPLIT_PATTERN = "(?<=[。！？；])";
    private static final int MAX_CHUNK_LENGTH = 200;

    public List<String> chunk(String text) {
        List<String> sentences = Arrays.stream(text.split(SENTENCE_SPLIT_PATTERN))
                .map(String::trim)
                .filter(sentence -> !sentence.isEmpty())
                .toList();
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (!current.isEmpty() && current.length() + sentence.length() > MAX_CHUNK_LENGTH) {
                chunks.add(current.toString());
                current = new StringBuilder();
            }
            current.append(sentence);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
