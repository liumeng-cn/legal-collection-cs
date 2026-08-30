package com.legalcs.dao;

import com.legalcs.common.MemoryStatus;
import com.legalcs.common.MemoryType;
import com.legalcs.entity.UserMemory;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MemoryDAO {

    private static final int DEFAULT_IMPORTANCE = 1;

    private static final String SELECT_ACTIVE_SEMANTIC =
            "SELECT content FROM user_memory "
                    + "WHERE user_id = ? AND memory_type = ? AND status = ? ORDER BY created_at";
    private static final String SELECT_ACTIVE_EPISODIC =
            "SELECT id, user_id, memory_type, content, embedding, importance, status, created_at, updated_at "
                    + "FROM user_memory WHERE user_id = ? AND memory_type = ? AND status = ? ORDER BY created_at";
    private static final String SEARCH_EPISODIC =
            "SELECT content FROM user_memory "
                    + "WHERE user_id = ? AND memory_type = ? AND status = ? "
                    + "ORDER BY embedding <=> ? LIMIT ?";
    private static final String INSERT =
            "INSERT INTO user_memory (user_id, memory_type, content, embedding, importance, status) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";
    private static final String MARK_ALL_SUPERSEDED =
            "UPDATE user_memory SET status = ?, updated_at = now() "
                    + "WHERE user_id = ? AND memory_type = ? AND status = ?";
    private static final String MARK_SUPERSEDED_BY_IDS =
            "UPDATE user_memory SET status = ?, updated_at = now() WHERE id = ANY(?) AND status = ?";
    private static final String MARK_EXPIRED_BY_IDS =
            "UPDATE user_memory SET status = ?, updated_at = now() WHERE id = ANY(?) AND status = ?";
    private static final String SELECT_EXPIRED_EPISODIC_IDS =
            "SELECT id FROM user_memory WHERE memory_type = ? AND status = ? AND updated_at < ?";

    private final JdbcTemplate chatJdbcTemplate;

    public List<String> findActiveSemanticContents(String userId) {
        return chatJdbcTemplate.query(SELECT_ACTIVE_SEMANTIC,
                (rs, rowNum) -> rs.getString("content"),
                userId, MemoryType.SEMANTIC.name(), MemoryStatus.ACTIVE.name());
    }

    public List<UserMemory> findActiveEpisodic(String userId) {
        return chatJdbcTemplate.query(SELECT_ACTIVE_EPISODIC,
                (rs, rowNum) -> new UserMemory(
                        rs.getLong("id"),
                        rs.getString("user_id"),
                        MemoryType.valueOf(rs.getString("memory_type")),
                        rs.getString("content"),
                        readVector(rs.getObject("embedding")),
                        rs.getInt("importance"),
                        MemoryStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime()),
                userId, MemoryType.EPISODIC.name(), MemoryStatus.ACTIVE.name());
    }

    public List<String> searchEpisodic(String userId, float[] embedding, int topK) {
        return chatJdbcTemplate.query(SEARCH_EPISODIC,
                (rs, rowNum) -> rs.getString("content"),
                userId, MemoryType.EPISODIC.name(), MemoryStatus.ACTIVE.name(), toVector(embedding), topK);
    }

    public void insert(String userId, MemoryType type, String content, float[] embedding) {
        chatJdbcTemplate.update(INSERT, userId, type.name(), content, toVector(embedding),
                DEFAULT_IMPORTANCE, MemoryStatus.ACTIVE.name());
    }

    public void markAllSuperseded(String userId, MemoryType type) {
        chatJdbcTemplate.update(MARK_ALL_SUPERSEDED, MemoryStatus.SUPERSEDED.name(),
                userId, type.name(), MemoryStatus.ACTIVE.name());
    }

    public void markSuperseded(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        chatJdbcTemplate.update(MARK_SUPERSEDED_BY_IDS, MemoryStatus.SUPERSEDED.name(),
                toPgArray(ids), MemoryStatus.ACTIVE.name());
    }

    public List<Long> findExpiredEpisodicIds(LocalDateTime cutoff) {
        return chatJdbcTemplate.query(SELECT_EXPIRED_EPISODIC_IDS,
                (rs, rowNum) -> rs.getLong("id"),
                MemoryType.EPISODIC.name(), MemoryStatus.ACTIVE.name(), cutoff);
    }

    public void markExpired(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        chatJdbcTemplate.update(MARK_EXPIRED_BY_IDS, MemoryStatus.EXPIRED.name(),
                toPgArray(ids), MemoryStatus.ACTIVE.name());
    }

    public static PGobject toVector(float[] embedding) {
        if (embedding == null) {
            return null;
        }
        PGobject vector = new PGobject();
        try {
            vector.setType("vector");
            vector.setValue(Arrays.toString(embedding));
            return vector;
        } catch (SQLException e) {
            throw new IllegalStateException("构建向量失败", e);
        }
    }

    private static float[] readVector(Object value) {
        if (value == null) {
            return null;
        }
        String raw = value instanceof PGobject pg ? pg.getValue() : value.toString();
        if (raw == null || raw.length() < 2) {
            return null;
        }
        String body = raw.substring(1, raw.length() - 1);
        String[] parts = body.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }

    private static String toPgArray(Collection<Long> ids) {
        return ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }
}
