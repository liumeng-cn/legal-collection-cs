package com.legalcs.chat;

import com.legalcs.common.MessageRole;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MessageDao {

    private static final String INSERT =
            "INSERT INTO message (conversation_id, role, content) VALUES (?, ?, ?)";
    private static final String SELECT_BY_CONVERSATION =
            "SELECT id, conversation_id, role, content, created_at "
                    + "FROM message WHERE conversation_id = ? ORDER BY created_at";

    private final JdbcTemplate chatJdbcTemplate;

    public void insert(long conversationId, MessageRole role, String content) {
        chatJdbcTemplate.update(INSERT, conversationId, role.name(), content);
    }

    public List<ChatMessage> findByConversationId(long conversationId) {
        return chatJdbcTemplate.query(SELECT_BY_CONVERSATION,
                (rs, rowNum) -> new ChatMessage(
                        rs.getLong("id"),
                        rs.getLong("conversation_id"),
                        MessageRole.valueOf(rs.getString("role")),
                        rs.getString("content"),
                        rs.getTimestamp("created_at").toLocalDateTime()),
                conversationId);
    }
}
