package com.legalcs.chat;

import com.legalcs.common.MessageRole;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ChatMessage {

    private final long id;
    private final long conversationId;
    private final MessageRole role;
    private final String content;
    private final LocalDateTime createdAt;
}
