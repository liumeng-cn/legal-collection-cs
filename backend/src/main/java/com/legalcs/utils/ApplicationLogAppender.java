package com.legalcs.utils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ApplicationLogAppender extends AppenderBase<ILoggingEvent> {

    private static final int QUEUE_CAPACITY = 10000;
    private static final int BATCH_SIZE = 200;
    private static final long DRAIN_TIMEOUT_MS = 500;
    private static final long ERROR_BACKOFF_MS = 2000;
    private static final int MAX_POOL_SIZE = 2;
    private static final String TRACE_ID_MDC_KEY = "trace_id";
    private static final String POOL_NAME = "application-log-appender";

    private static final String INSERT_SQL = """
            INSERT INTO application_log (ts, level, logger, thread, trace_id, message, stack_trace, payload)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            """;

    private String jdbcUrl;
    private String username;
    private String password;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BlockingQueue<ILoggingEvent> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private HikariDataSource dataSource;
    private ExecutorService worker;

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public void start() {
        if (isBlank(jdbcUrl)) {
            addError("jdbcUrl 未配置，日志直写禁用");
            return;
        }
        try {
            this.dataSource = new HikariDataSource(buildHikariConfig());
            this.running.set(true);
            this.worker = Executors.newSingleThreadExecutor(this::newWriterThread);
            this.worker.submit(this::drainLoop);
            super.start();
        } catch (RuntimeException e) {
            addError("日志直写初始化失败", e);
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (running.get()) {
            queue.offer(event);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        if (worker != null) {
            worker.shutdownNow();
        }
        if (dataSource != null) {
            dataSource.close();
        }
        super.stop();
    }

    private HikariConfig buildHikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(0);
        config.setPoolName(POOL_NAME);
        return config;
    }

    private Thread newWriterThread(Runnable task) {
        Thread thread = new Thread(task, "application-log-writer");
        thread.setDaemon(true);
        return thread;
    }

    private void drainLoop() {
        List<ILoggingEvent> batch = new ArrayList<>(BATCH_SIZE);
        while (running.get()) {
            try {
                batch.clear();
                ILoggingEvent first = queue.poll(DRAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    queue.drainTo(batch, BATCH_SIZE - 1);
                }
                if (!batch.isEmpty()) {
                    writeBatch(batch);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                addError("日志批量写入失败", e);
                sleepQuietly();
            }
        }
        flushRemaining();
    }

    private void flushRemaining() {
        List<ILoggingEvent> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            try {
                writeBatch(remaining);
            } catch (RuntimeException e) {
                addError("日志剩余批量写入失败", e);
            }
        }
    }

    private void writeBatch(List<ILoggingEvent> batch) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            for (ILoggingEvent event : batch) {
                bind(statement, event);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void bind(PreparedStatement statement, ILoggingEvent event) throws Exception {
        statement.setTimestamp(1, Timestamp.from(Instant.ofEpochMilli(event.getTimeStamp())));
        statement.setString(2, event.getLevel().toString());
        statement.setString(3, event.getLoggerName());
        statement.setString(4, event.getThreadName());
        statement.setString(5, event.getMDCPropertyMap().get(TRACE_ID_MDC_KEY));
        statement.setString(6, event.getFormattedMessage());
        statement.setString(7, resolveStackTrace(event));
        statement.setString(8, buildPayloadJson(event));
    }

    private String resolveStackTrace(ILoggingEvent event) {
        return event.getThrowableProxy() == null
                ? null
                : ThrowableProxyUtil.asString(event.getThrowableProxy());
    }

    private String buildPayloadJson(ILoggingEvent event) {
        ObjectNode node = objectMapper.createObjectNode();
        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc != null) {
            mdc.forEach(node::put);
        }
        return node.toString();
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(ERROR_BACKOFF_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
