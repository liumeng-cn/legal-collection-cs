package com.legalcs.aiops.tool;

import com.legalcs.auth.AuthContext;
import com.legalcs.common.Role;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LogRetrievalTool {

    private static final int MAX_RESULTS = 50;
    private static final int FETCH_LIMIT = 200;
    private static final int STACK_TRACE_MAX_LENGTH = 1000;
    private static final String DENY_MESSAGE = "无权访问：仅产研运维（SRE）可调用日志检索";
    private static final Set<String> ERROR_LEVELS = Set.of("ERROR", "FATAL");

    private static final String COUNT_SQL = """
            SELECT COUNT(*)
            FROM application_log
            WHERE ts >= COALESCE(?::timestamptz, '-infinity'::timestamptz)
              AND ts <= COALESCE(?::timestamptz, 'infinity'::timestamptz)
              AND (? IS NULL OR level = ?)
              AND (? IS NULL OR search_vector @@ plainto_tsquery('simple', ?))
            """;

    private static final String SEARCH_SQL = """
            SELECT ts, level, logger, thread, trace_id, message, stack_trace
            FROM application_log
            WHERE ts >= COALESCE(?::timestamptz, '-infinity'::timestamptz)
              AND ts <= COALESCE(?::timestamptz, 'infinity'::timestamptz)
              AND (? IS NULL OR level = ?)
              AND (? IS NULL OR search_vector @@ plainto_tsquery('simple', ?))
            ORDER BY ts DESC
            LIMIT ?
            """;

    private static final String TIMELINE_COUNT_SQL = """
            SELECT COUNT(*) FROM application_log WHERE trace_id = ?
            """;

    private static final String TIMELINE_SQL = """
            SELECT ts, level, logger, thread, trace_id, message, stack_trace
            FROM application_log
            WHERE trace_id = ?
            ORDER BY ts ASC
            LIMIT ?
            """;

    private final JdbcTemplate logsJdbcTemplate;

    @Tool(name = "search_logs", description = "按时间范围、级别与关键字检索应用日志，返回命中日志及堆栈摘要，仅产研运维可调")
    public String searchLogs(
            @ToolParam(name = "keyword", description = "关键字，匹配 message 或 stack_trace，可空") String keyword,
            @ToolParam(name = "level", description = "日志级别，如 ERROR/WARN，可空") String level,
            @ToolParam(name = "start_time", description = "起始时间 ISO-8601，如 2026-08-20T08:00:00，可空") String startTime,
            @ToolParam(name = "end_time", description = "结束时间 ISO-8601，可空") String endTime,
            AuthContext authContext) {
        if (authContext.getRole() != Role.SRE) {
            return DENY_MESSAGE;
        }
        try {
            long total = countRows(startTime, endTime, level, keyword);
            List<Map<String, Object>> rows = logsJdbcTemplate.queryForList(
                    SEARCH_SQL, startTime, endTime, level, level, keyword, keyword, FETCH_LIMIT);
            return postProcessSearch(rows, keyword, total);
        } catch (RuntimeException e) {
            return "日志检索失败：" + e.getMessage();
        }
    }

    @Tool(name = "trace_timeline", description = "按 trace_id 还原一条请求的完整日志时间线，仅产研运维可调")
    public String traceTimeline(
            @ToolParam(name = "trace_id", description = "链路追踪 ID") String traceId,
            AuthContext authContext) {
        if (authContext.getRole() != Role.SRE) {
            return DENY_MESSAGE;
        }
        try {
            long total = countTraceRows(traceId);
            List<Map<String, Object>> rows = logsJdbcTemplate.queryForList(TIMELINE_SQL, traceId, FETCH_LIMIT);
            return postProcessTimeline(rows, total);
        } catch (RuntimeException e) {
            return "日志检索失败：" + e.getMessage();
        }
    }

    private long countRows(String startTime, String endTime, String level, String keyword) {
        Long count = logsJdbcTemplate.queryForObject(
                COUNT_SQL, Long.class, startTime, endTime, level, level, keyword, keyword);
        return count == null ? 0L : count;
    }

    private long countTraceRows(String traceId) {
        Long count = logsJdbcTemplate.queryForObject(TIMELINE_COUNT_SQL, Long.class, traceId);
        return count == null ? 0L : count;
    }

    private String postProcessSearch(List<Map<String, Object>> rows, String keyword, long total) {
        if (rows.isEmpty()) {
            return "无命中日志";
        }
        String normalizedKeyword = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> kept = prioritize(rows, normalizedKeyword);
        StringBuilder builder = new StringBuilder();
        builder.append("命中 ").append(total).append(" 条日志，优先保留 ERROR/关键字相关 ")
                .append(kept.size()).append(" 条：\n");
        builder.append(kept.stream().map(this::formatRow).collect(Collectors.joining("\n")));
        if (kept.size() < rows.size() || rows.size() < total) {
            builder.append("\n").append(summarizeDropped(rows, kept.size(), total));
        }
        return builder.toString();
    }

    private String postProcessTimeline(List<Map<String, Object>> rows, long total) {
        if (rows.isEmpty()) {
            return "无命中日志";
        }
        List<Map<String, Object>> kept = prioritize(rows, "");
        StringBuilder builder = new StringBuilder();
        builder.append("该 trace 共 ").append(total).append(" 条日志，已按 ERROR 优先返回 ")
                .append(kept.size()).append(" 条：\n");
        builder.append(kept.stream().map(this::formatRow).collect(Collectors.joining("\n")));
        if (kept.size() < rows.size() || rows.size() < total) {
            builder.append("\n").append(summarizeDropped(rows, kept.size(), total));
        }
        return builder.toString();
    }

    private List<Map<String, Object>> prioritize(List<Map<String, Object>> rows, String keyword) {
        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> keywordHits = new ArrayList<>();
        List<Map<String, Object>> others = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (isError(row)) {
                errors.add(row);
            } else if (!keyword.isEmpty() && matchesKeyword(row, keyword)) {
                keywordHits.add(row);
            } else {
                others.add(row);
            }
        }
        List<Map<String, Object>> kept = new ArrayList<>();
        addUntil(kept, errors, MAX_RESULTS);
        addUntil(kept, keywordHits, MAX_RESULTS);
        addUntil(kept, others, MAX_RESULTS);
        return kept;
    }

    private void addUntil(List<Map<String, Object>> target, List<Map<String, Object>> source, int limit) {
        for (Map<String, Object> row : source) {
            if (target.size() >= limit) {
                return;
            }
            target.add(row);
        }
    }

    private boolean isError(Map<String, Object> row) {
        Object level = row.get("level");
        return level != null && ERROR_LEVELS.contains(level.toString().toUpperCase(Locale.ROOT));
    }

    private boolean matchesKeyword(Map<String, Object> row, String keyword) {
        return containsIgnoreCase(row.get("message"), keyword) || containsIgnoreCase(row.get("stack_trace"), keyword);
    }

    private boolean containsIgnoreCase(Object value, String keyword) {
        return value != null && value.toString().toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String summarizeDropped(List<Map<String, Object>> rows, int keptCount, long total) {
        Map<String, Long> levelCounts = rows.stream()
                .collect(Collectors.groupingBy(this::levelOf, Collectors.counting()));
        String distribution = levelCounts.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue() + " 条")
                .collect(Collectors.joining("，"));
        return "...（共命中 " + total + " 条，已返回前 " + keptCount
                + " 条；取回样本级别分布：" + distribution + "）";
    }

    private String levelOf(Map<String, Object> row) {
        Object level = row.get("level");
        return level == null ? "UNKNOWN" : level.toString();
    }

    private String formatRow(Map<String, Object> row) {
        StringBuilder builder = new StringBuilder();
        builder.append("[").append(row.get("ts")).append("] ")
                .append("[").append(row.get("level")).append("] ")
                .append("[").append(row.get("logger")).append("] ");
        if (row.get("trace_id") != null) {
            builder.append("[trace_id=").append(row.get("trace_id")).append("] ");
        }
        builder.append(row.get("message"));
        String stackTrace = (String) row.get("stack_trace");
        if (stackTrace != null && !stackTrace.isBlank()) {
            builder.append("\n").append(truncate(stackTrace, STACK_TRACE_MAX_LENGTH));
        }
        return builder.toString();
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
