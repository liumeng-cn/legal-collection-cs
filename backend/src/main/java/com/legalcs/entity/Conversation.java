package com.legalcs.entity;

import com.legalcs.common.Role;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class Conversation {

    private final long id;
    private final String userId;
    private final Role role;
    private final String title;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
