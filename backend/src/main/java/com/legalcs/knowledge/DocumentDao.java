package com.legalcs.knowledge;

import com.legalcs.common.Role;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DocumentDao {

    private static final String SELECT_ALL_DOCUMENTS =
            "SELECT id, title, content, allowed_roles, case_id FROM document";
    private static final String INSERT_CHUNK =
            "INSERT INTO document_chunk (document_id, chunk_text, embedding) VALUES (?, ?, ?)";
    private static final String DELETE_CHUNKS = "DELETE FROM document_chunk WHERE document_id = ?";
    private static final String COUNT_CHUNKS = "SELECT count(*) FROM document_chunk";
    private static final String SEARCH_SIMILAR =
            "SELECT d.title, c.chunk_text, 1 - (c.embedding <=> ?) AS score "
                    + "FROM document_chunk c JOIN document d ON d.id = c.document_id "
                    + "WHERE (d.allowed_roles IS NULL OR ? = ANY(d.allowed_roles)) "
                    + "AND (d.case_id IS NULL OR ? IN ('" + Role.STAFF.name() + "','" + Role.SRE.name() + "') "
                    + "OR d.case_id = ANY(?::bigint[])) "
                    + "ORDER BY c.embedding <=> ? "
                    + "LIMIT ?";

    private final JdbcTemplate knowledgeJdbcTemplate;

    public List<KnowledgeDocument> findAll() {
        return knowledgeJdbcTemplate.query(SELECT_ALL_DOCUMENTS,
                (rs, rowNum) -> new KnowledgeDocument(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("content"),
                        readStringArray(rs.getArray("allowed_roles")),
                        (Long) rs.getObject("case_id")));
    }

    public long countChunks() {
        Long count = knowledgeJdbcTemplate.queryForObject(COUNT_CHUNKS, Long.class);
        return count == null ? 0L : count;
    }

    public void insertChunk(long documentId, String chunkText, float[] embedding) {
        knowledgeJdbcTemplate.update(INSERT_CHUNK, documentId, chunkText, toVector(embedding));
    }

    public void deleteChunks(long documentId) {
        knowledgeJdbcTemplate.update(DELETE_CHUNKS, documentId);
    }

    public List<RagChunk> search(float[] embedding, int topK, String role, List<Long> caseIds) {
        PGobject vector = toVector(embedding);
        return knowledgeJdbcTemplate.query(SEARCH_SIMILAR,
                (rs, rowNum) -> new RagChunk(
                        rs.getString("title"),
                        rs.getString("chunk_text"),
                        rs.getDouble("score")),
                vector, role, role, toPgArray(caseIds), vector, topK);
    }

    public static String toPgArray(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "{}";
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }

    private static List<String> readStringArray(java.sql.Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        return Arrays.asList((String[]) array.getArray());
    }

    private static PGobject toVector(float[] embedding) {
        PGobject vector = new PGobject();
        try {
            vector.setType("vector");
            vector.setValue(Arrays.toString(embedding));
            return vector;
        } catch (SQLException e) {
            throw new IllegalStateException("构建向量失败", e);
        }
    }
}
