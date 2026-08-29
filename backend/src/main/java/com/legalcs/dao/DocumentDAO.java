package com.legalcs.dao;

import com.legalcs.common.Role;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.legalcs.entity.KnowledgeDocument;
import com.legalcs.entity.RagChunk;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DocumentDAO {

    private static final String SELECT_ALL_DOCUMENTS =
            "SELECT id, title, content, allowed_roles, case_id FROM document";
    private static final String INSERT_CHUNK =
            "INSERT INTO document_chunk (document_id, chunk_index, chunk_text, embedding) VALUES (?, ?, ?, ?)";
    private static final String DELETE_CHUNKS = "DELETE FROM document_chunk WHERE document_id = ?";
    private static final String COUNT_CHUNKS = "SELECT count(*) FROM document_chunk";
    private static final String SEARCH_SIMILAR =
            "SELECT c.id, c.document_id, c.chunk_index, d.title, c.chunk_text, 1 - (c.embedding <=> ?) AS score "
                    + "FROM document_chunk c JOIN document d ON d.id = c.document_id "
                    + "WHERE (d.allowed_roles IS NULL OR ? = ANY(d.allowed_roles)) "
                    + "AND (d.case_id IS NULL OR ? IN ('" + Role.STAFF.name() + "','" + Role.SRE.name() + "') "
                    + "OR d.case_id = ANY(?::bigint[])) "
                    + "ORDER BY c.embedding <=> ? "
                    + "LIMIT ?";
    private static final String SEARCH_KEYWORD =
            "SELECT c.id, c.document_id, c.chunk_index, d.title, c.chunk_text, "
                    + "ts_rank(c.chunk_text_tsv, plainto_tsquery('zh', ?)) AS score "
                    + "FROM document_chunk c JOIN document d ON d.id = c.document_id "
                    + "WHERE c.chunk_text_tsv @@ plainto_tsquery('zh', ?) "
                    + "AND (d.allowed_roles IS NULL OR ? = ANY(d.allowed_roles)) "
                    + "AND (d.case_id IS NULL OR ? IN ('" + Role.STAFF.name() + "','" + Role.SRE.name() + "') "
                    + "OR d.case_id = ANY(?::bigint[])) "
                    + "ORDER BY score DESC "
                    + "LIMIT ?";
    private static final String EXPAND_NEIGHBORS =
            "SELECT d.title, c.chunk_text FROM document_chunk c "
                    + "JOIN document d ON d.id = c.document_id "
                    + "WHERE c.document_id = ? AND c.chunk_index BETWEEN ? AND ? "
                    + "AND (d.allowed_roles IS NULL OR ? = ANY(d.allowed_roles)) "
                    + "AND (d.case_id IS NULL OR ? IN ('" + Role.STAFF.name() + "','" + Role.SRE.name() + "') "
                    + "OR d.case_id = ANY(?::bigint[])) "
                    + "ORDER BY c.chunk_index";
    private static final String EXPAND_FULL =
            "SELECT title, content FROM document "
                    + "WHERE id = ? "
                    + "AND (allowed_roles IS NULL OR ? = ANY(allowed_roles)) "
                    + "AND (case_id IS NULL OR ? IN ('" + Role.STAFF.name() + "','" + Role.SRE.name() + "') "
                    + "OR case_id = ANY(?::bigint[]))";

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

    public void insertChunk(long documentId, int chunkIndex, String chunkText, float[] embedding) {
        knowledgeJdbcTemplate.update(INSERT_CHUNK, documentId, chunkIndex, chunkText, toVector(embedding));
    }

    public void deleteChunks(long documentId) {
        knowledgeJdbcTemplate.update(DELETE_CHUNKS, documentId);
    }

    public List<RagChunk> search(float[] embedding, int topK, String role, List<Long> caseIds) {
        PGobject vector = toVector(embedding);
        return knowledgeJdbcTemplate.query(SEARCH_SIMILAR,
                (rs, rowNum) -> new RagChunk(
                        rs.getLong("id"),
                        rs.getLong("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("title"),
                        rs.getString("chunk_text"),
                        rs.getDouble("score")),
                vector, role, role, toPgArray(caseIds), vector, topK);
    }

    public List<RagChunk> searchKeyword(String query, int topK, String role, List<Long> caseIds) {
        return knowledgeJdbcTemplate.query(SEARCH_KEYWORD,
                (rs, rowNum) -> new RagChunk(
                        rs.getLong("id"),
                        rs.getLong("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("title"),
                        rs.getString("chunk_text"),
                        rs.getDouble("score")),
                query, query, role, role, toPgArray(caseIds), topK);
    }

    public List<String> expandNeighbors(long documentId, int chunkIndex, int window, String role, List<Long> caseIds) {
        return knowledgeJdbcTemplate.query(EXPAND_NEIGHBORS,
                (rs, rowNum) -> "【" + rs.getString("title") + "】" + rs.getString("chunk_text"),
                documentId, chunkIndex - window, chunkIndex + window,
                role, role, toPgArray(caseIds));
    }

    public String expandFull(long documentId, String role, List<Long> caseIds) {
        List<String> result = knowledgeJdbcTemplate.query(EXPAND_FULL,
                (rs, rowNum) -> "【" + rs.getString("title") + "】" + rs.getString("content"),
                documentId, role, role, toPgArray(caseIds));
        return result.isEmpty() ? null : result.getFirst();
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
