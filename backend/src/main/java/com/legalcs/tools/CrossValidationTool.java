package com.legalcs.tools;

import com.legalcs.service.auth.AuthContext;
import com.legalcs.common.Role;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 交叉验证的确定性规则部分（硬校验，机械可判）。
 * 软校验（语义对齐）由主控 LLM 完成，见 AiopsAgentFactory 系统提示。
 */
@Component
@RequiredArgsConstructor
public class CrossValidationTool {

    private static final String DENY_MESSAGE = "无权访问：仅产研运维（SRE）可调用交叉验证";
    private static final long DEFAULT_WINDOW_SECONDS = 300;

    private static final String TRACE_STATS_SQL = """
            SELECT COUNT(*) AS total,
                   MIN(ts) AS first_ts,
                   MAX(ts) AS last_ts,
                   COUNT(*) FILTER (WHERE level IN ('ERROR','FATAL')) AS error_count,
                   COUNT(*) FILTER (WHERE level IN ('INFO','DEBUG','WARN')) AS entry_count
            FROM application_log
            WHERE trace_id = ?
            """;

    private final JdbcTemplate logsJdbcTemplate;

    @Tool(name = "verify_trace_coverage",
            description = "确定性校验：验证 trace_id 是否贯穿整条请求日志（含入口与错误日志、时间线是否连续），返回结构化判定")
    public String verifyTraceCoverage(
            @ToolParam(name = "trace_id", description = "链路追踪 ID") String traceId,
            AuthContext authContext) {
        if (authContext.getRole() != Role.SRE) {
            return DENY_MESSAGE;
        }
        try {
            Map<String, Object> stats = logsJdbcTemplate.queryForMap(TRACE_STATS_SQL, traceId);
            long total = asLong(stats.get("total"));
            if (total == 0) {
                return "确定性校验：该 trace_id 无任何日志，无法验证链路完整性";
            }
            long errorCount = asLong(stats.get("error_count"));
            long entryCount = asLong(stats.get("entry_count"));
            Object firstTs = stats.get("first_ts");
            Object lastTs = stats.get("last_ts");

            boolean hasEntry = entryCount > 0;
            boolean hasError = errorCount > 0;
            boolean multiNode = total >= 2 && firstTs != null && lastTs != null && !firstTs.equals(lastTs);

            StringBuilder builder = new StringBuilder();
            builder.append("确定性校验结果：\n")
                    .append("- 日志条数：").append(total).append("\n")
                    .append("- 时间跨度：").append(firstTs).append(" → ").append(lastTs).append("\n")
                    .append("- 入口日志存在性：").append(hasEntry ? "通过（含 INFO/DEBUG/WARN，链路有入口）" : "失败（仅 ERROR，缺入口日志，trace 疑似被截断）").append("\n")
                    .append("- 错误日志存在性：").append(hasError ? "通过（含 ERROR/FATAL，定位到异常点）" : "未发现异常（无 ERROR 日志）").append("\n")
                    .append("- 链路连续性：").append(multiNode ? "通过（跨多个时间点）" : "失败（仅单条日志，无法还原完整链路）").append("\n");
            if (hasEntry && hasError && multiNode) {
                builder.append("结论：trace 贯穿完整，时间线可用于交叉验证");
            } else {
                builder.append("结论：trace 证据不完整，需回溯补充或换方向取证");
            }
            return builder.toString();
        } catch (RuntimeException e) {
            return "交叉验证失败：" + e.getMessage();
        }
    }

    @Tool(name = "verify_time_alignment",
            description = "确定性校验：验证两个时间戳差是否在给定窗口内，用于对齐报错时间与数据变更时间")
    public String verifyTimeAlignment(
            @ToolParam(name = "error_time", description = "报错时间戳（ISO-8601，可带偏移）") String errorTime,
            @ToolParam(name = "reference_time", description = "参照时间戳，如数据变更时间（ISO-8601）") String referenceTime,
            @ToolParam(name = "window_seconds", description = "允许的偏差窗口（秒），缺省 300") String windowSeconds,
            AuthContext authContext) {
        if (authContext.getRole() != Role.SRE) {
            return DENY_MESSAGE;
        }
        try {
            Instant error = parseTimestamp(errorTime);
            Instant reference = parseTimestamp(referenceTime);
            long window = parseWindow(windowSeconds);
            long deltaMillis = Math.abs(Duration.between(error, reference).toMillis());
            double deltaSeconds = deltaMillis / 1000.0;
            if (deltaMillis <= window * 1000L) {
                return "对齐通过：两时间戳相差 " + deltaSeconds + " 秒，在窗口 " + window + " 秒内，证据时间线一致";
            }
            return "矛盾：两时间戳相差 " + deltaSeconds + " 秒，超出窗口 " + window
                    + " 秒，证据时间线不对齐，需回溯复核或更换假设";
        } catch (RuntimeException e) {
            return "时间对齐校验失败：" + e.getMessage();
        }
    }

    private long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private long parseWindow(String windowSeconds) {
        if (windowSeconds == null || windowSeconds.isBlank()) {
            return DEFAULT_WINDOW_SECONDS;
        }
        return Long.parseLong(windowSeconds.trim());
    }

    private Instant parseTimestamp(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("时间戳为空");
        }
        String normalized = text.trim().replace(' ', 'T');
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException ignored) {
            // try with offset
        }
        try {
            return OffsetDateTime.parse(normalized).toInstant();
        } catch (DateTimeParseException ignored) {
            // try without offset, assume UTC
        }
        try {
            return LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("无法解析时间戳：" + text);
        }
    }
}
