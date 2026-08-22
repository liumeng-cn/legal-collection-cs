package com.legalcs.chat;

import com.legalcs.common.Role;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ConversationDao {

    private static final String INSERT =
            "INSERT INTO conversation (user_id, role, title) VALUES (?, ?, ?) RETURNING id";
    private static final String SELECT_BY_USER =
            "SELECT id, user_id, role, title, created_at, updated_at "
                    + "FROM conversation WHERE user_id = ? ORDER BY updated_at DESC";
    private static final String UPDATE_TIMESTAMP =
            "UPDATE conversation SET updated_at = now() WHERE id = ?";

    private final JdbcTemplate chatJdbcTemplate;

    public long create(String userId, String role, String title) {
        return chatJdbcTemplate.queryForObject(INSERT, Long.class, userId, role, title);
    }

    public List<Conversation> findByUserId(String userId) {
        return chatJdbcTemplate.query(SELECT_BY_USER,
                (rs, rowNum) -> new Conversation(
                        rs.getLong("id"),
                        rs.getString("user_id"),
                        Role.valueOf(rs.getString("role")),
                        rs.getString("title"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime()),
                userId);
    }

    public void updateTimestamp(long conversationId) {
        chatJdbcTemplate.update(UPDATE_TIMESTAMP, conversationId);
    }
}
