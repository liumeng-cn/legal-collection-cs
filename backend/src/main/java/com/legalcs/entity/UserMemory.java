package com.legalcs.entity;

import com.legalcs.common.MemoryStatus;
import com.legalcs.common.MemoryType;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UserMemory {

    private final long id;
    private final String userId;
    private final MemoryType memoryType;
    private final String content;
    private final float[] embedding;
    private final int importance;
    private final MemoryStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
