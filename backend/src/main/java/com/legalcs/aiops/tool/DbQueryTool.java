package com.legalcs.aiops.tool;

import com.legalcs.auth.AuthContext;
import com.legalcs.common.Role;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DbQueryTool {

    private static final String DENY_MESSAGE = "无权访问：仅产研运维（SRE）可调用数据库查询";
    private static final int MAX_ROWS = 50;

    private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|GRANT|REVOKE|MERGE|REPLACE|COPY|CALL|EXECUTE|COMMENT)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "\\b(?:FROM|JOIN)\\s+([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);

    private static final Map<String, Set<String>> TABLE_WHITELIST = Map.of(
            "auth", Set.of("debtor", "debtor_case_binding"),
            "business", Set.of("case_info", "debt_detail", "repayment_record"),
            "knowledge", Set.of("document", "document_chunk"),
            "chat", Set.of("conversation", "message"));

    private final JdbcTemplate authJdbcTemplate;
    private final JdbcTemplate businessJdbcTemplate;
    private final JdbcTemplate knowledgeJdbcTemplate;
    private final JdbcTemplate chatJdbcTemplate;

    @Tool(name = "query_database", description = "对指定业务库执行只读 SELECT 查询，表名受白名单约束，仅产研运维可调")
    public String queryDatabase(
            @ToolParam(name = "database", description = "目标库：auth/business/knowledge/chat") String database,
            @ToolParam(name = "sql", description = "只读 SELECT 语句") String sql,
            AuthContext authContext) {
        if (authContext.getRole() != Role.SRE) {
            return DENY_MESSAGE;
        }
        String dbKey = normalizeDatabase(database);
        if (!TABLE_WHITELIST.containsKey(dbKey)) {
            return "拒绝：未知数据库 " + database + "，仅支持 auth/business/knowledge/chat";
        }
        if (!isReadOnly(sql)) {
            return "拒绝：仅允许只读 SELECT，检测到写操作或禁止关键字";
        }
        String tableViolation = findTableViolation(sql, TABLE_WHITELIST.get(dbKey));
        if (tableViolation != null) {
            return "拒绝：表 " + tableViolation + " 不在白名单内";
        }
        try {
            List<Map<String, Object>> rows = resolveTemplate(dbKey).queryForList(sql);
            return formatRows(rows);
        } catch (RuntimeException e) {
            return "查询失败：" + e.getMessage();
        }
    }

    private boolean isReadOnly(String sql) {
        if (sql == null || sql.isBlank()) {
            return false;
        }
        String normalized = sql.trim();
        if (normalized.contains(";")) {
            return false;
        }
        if (!normalized.toUpperCase(Locale.ROOT).startsWith("SELECT")) {
            return false;
        }
        return !FORBIDDEN_PATTERN.matcher(normalized).find();
    }

    private String findTableViolation(String sql, Set<String> allowed) {
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        while (matcher.find()) {
            String table = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!allowed.contains(table)) {
                return table;
            }
        }
        return null;
    }

    private String normalizeDatabase(String database) {
        return database == null ? "" : database.trim().toLowerCase(Locale.ROOT);
    }

    private JdbcTemplate resolveTemplate(String dbKey) {
        return switch (dbKey) {
            case "auth" -> authJdbcTemplate;
            case "business" -> businessJdbcTemplate;
            case "knowledge" -> knowledgeJdbcTemplate;
            case "chat" -> chatJdbcTemplate;
            default -> throw new IllegalArgumentException("未知数据库 " + dbKey);
        };
    }

    private String formatRows(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "查询结果为空";
        }
        String result = rows.stream()
                .limit(MAX_ROWS)
                .map(Map::toString)
                .collect(Collectors.joining("\n"));
        return rows.size() > MAX_ROWS
                ? result + "\n...（共 " + rows.size() + " 行，已截断至前 " + MAX_ROWS + " 行）"
                : result;
    }
}
