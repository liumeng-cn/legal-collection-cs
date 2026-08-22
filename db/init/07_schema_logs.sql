-- ============================================================
-- logs 库：应用日志（智能排障取证用）
-- 按天分区 + tsvector 全文检索 + trace_id 时间线还原
-- ============================================================

\c logs

CREATE TABLE application_log (
    ts            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    level         VARCHAR(16)  NOT NULL,
    logger        VARCHAR(255),
    thread        VARCHAR(255),
    trace_id      VARCHAR(64),
    message       TEXT,
    stack_trace   TEXT,
    payload       JSONB,
    search_vector tsvector GENERATED ALWAYS AS
        (to_tsvector('simple', coalesce(message, '') || ' ' || coalesce(stack_trace, ''))) STORED
) PARTITION BY RANGE (ts);

-- 默认分区：兜底未预建分区日期的日志，保证任意时间戳都能落库
CREATE TABLE application_log_default PARTITION OF application_log DEFAULT;

CREATE INDEX idx_application_log_search ON application_log USING GIN (search_vector);
CREATE INDEX idx_application_log_trace  ON application_log (trace_id);
CREATE INDEX idx_application_log_ts     ON application_log (ts);
CREATE INDEX idx_application_log_level  ON application_log (level);

-- 按天分区示例（生产由定时任务预建次日分区，或用 pg_partman 管理）：
-- CREATE TABLE application_log_20260820 PARTITION OF application_log
--     FOR VALUES FROM ('2026-08-20') TO ('2026-08-21');
