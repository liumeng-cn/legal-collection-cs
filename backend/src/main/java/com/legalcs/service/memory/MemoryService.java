package com.legalcs.service.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legalcs.client.DashScopeClient;
import com.legalcs.client.DeepSeekClient;
import com.legalcs.common.MemoryType;
import com.legalcs.config.MemoryProperties;
import com.legalcs.dao.MemoryDAO;
import com.legalcs.dao.MessageDAO;
import com.legalcs.dto.CompletionMessage;
import com.legalcs.dto.MemorySummaryResult;
import com.legalcs.entity.ChatMessage;
import com.legalcs.entity.UserMemory;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private static final String MEMORY_HEADER =
            "以下是该用户的历史记忆（仅供你了解用户背景，回答时自然融入，不得直接照搬原文）：";
    private static final double SUMMARY_TEMPERATURE = 0.3;

    private static final String SUMMARY_PROMPT = """
            你是一个记忆抽取器。阅读下面的对话，抽取需要长期记住的用户信息。

            输出要求：
            1. 只输出一个 JSON 对象，不要输出任何其他文字、注释或 markdown 代码块。
            2. JSON 结构：{"semantic": ["画像1", "画像2"], "episodic": ["情节1", "情节2"]}
            3. semantic（画像）：用户的稳定身份、偏好、习惯，例如「用户偏好用手机操作」「用户是某公司催收员」。
            4. episodic（情节）：与案件、欠款、还款承诺相关的具体事件，例如「用户承诺 8 月 29 日还款 2000 元」「用户已还清案件 A-1001」。
            5. 严禁编造对话中不存在的信息；没有可抽取内容时对应数组为空。
            6. 已有画像（供合并去重参考，不要原样重复）：%s

            对话内容：
            %s
            """;

    private final MemoryDAO memoryDAO;
    private final DashScopeClient dashScopeClient;
    private final DeepSeekClient deepSeekClient;
    private final MessageDAO messageDAO;
    private final MemoryProperties properties;
    private final ObjectMapper objectMapper;

    public String load(String userId, String query) {
        try {
            return doLoad(userId, query);
        } catch (Exception e) {
            log.warn("记忆加载失败 userId={} error={}", userId, e.toString());
            return "";
        }
    }

    private String doLoad(String userId, String query) {
        List<String> semantic = memoryDAO.findActiveSemanticContents(userId);
        List<String> episodic = (query == null || query.isBlank())
                ? List.of()
                : memoryDAO.searchEpisodic(userId, dashScopeClient.embed(query), properties.topK());
        if (semantic.isEmpty() && episodic.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(MEMORY_HEADER);
        if (!semantic.isEmpty()) {
            sb.append("\n【画像】");
            semantic.forEach(s -> sb.append("\n- ").append(s));
        }
        if (!episodic.isEmpty()) {
            sb.append("\n【相关情节】");
            episodic.forEach(s -> sb.append("\n- ").append(s));
        }
        return sb.toString();
    }

    public void saveAsync(String userId, long conversationId) {
        Mono.fromRunnable(() -> save(userId, conversationId))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        unused -> log.info("记忆摘要完成 userId={} conversationId={}", userId, conversationId),
                        err -> log.warn("记忆摘要失败 userId={} conversationId={} error={}",
                                userId, conversationId, err.toString()));
    }

    private void save(String userId, long conversationId) {
        List<ChatMessage> messages = messageDAO.findByConversationId(conversationId);
        if (messages.isEmpty()) {
            return;
        }
        String existingSemantic = String.join("\n", memoryDAO.findActiveSemanticContents(userId));
        String transcript = messages.stream()
                .map(m -> m.getRole().name() + "：" + m.getContent())
                .collect(Collectors.joining("\n"));
        String prompt = SUMMARY_PROMPT.formatted(existingSemantic, transcript);
        String raw = deepSeekClient.chat(List.of(new CompletionMessage("user", prompt)), SUMMARY_TEMPERATURE);
        MemorySummaryResult result = parse(raw);
        if (result == null) {
            return;
        }
        upsertSemantic(userId, result.semantic());
        upsertEpisodic(userId, result.episodic());
    }

    private void upsertSemantic(String userId, List<String> semantic) {
        if (semantic == null || semantic.isEmpty()) {
            return;
        }
        memoryDAO.markAllSuperseded(userId, MemoryType.SEMANTIC);
        semantic.stream().filter(s -> s != null && !s.isBlank())
                .forEach(s -> memoryDAO.insert(userId, MemoryType.SEMANTIC, s, null));
    }

    private void upsertEpisodic(String userId, List<String> episodic) {
        if (episodic == null || episodic.isEmpty()) {
            return;
        }
        List<UserMemory> existing = memoryDAO.findActiveEpisodic(userId);
        for (String item : episodic) {
            if (item == null || item.isBlank()) {
                continue;
            }
            float[] embedding = dashScopeClient.embed(item);
            List<Long> conflicts = new ArrayList<>();
            for (UserMemory memory : existing) {
                if (memory.getEmbedding() != null
                        && cosineSimilarity(memory.getEmbedding(), embedding) >= properties.similarityThreshold()) {
                    conflicts.add(memory.getId());
                }
            }
            if (!conflicts.isEmpty()) {
                memoryDAO.markSuperseded(conflicts);
            }
            memoryDAO.insert(userId, MemoryType.EPISODIC, item, embedding);
        }
    }

    @Scheduled(fixedDelayString = "${app.memory.expire-interval-ms}", initialDelayString = "${app.memory.expire-interval-ms}")
    public void expire() {
        if (!properties.enabled()) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(properties.retentionDays());
        List<Long> ids = memoryDAO.findExpiredEpisodicIds(cutoff);
        if (!ids.isEmpty()) {
            memoryDAO.markExpired(ids);
            log.info("记忆衰减标记 {} 条为 EXPIRED", ids.size());
        }
    }

    private MemorySummaryResult parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String json = extractJson(raw);
            return objectMapper.readValue(json, MemorySummaryResult.class);
        } catch (Exception e) {
            log.warn("记忆摘要 JSON 解析失败: {}", raw, e);
            return null;
        }
    }

    private static String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < start) {
            return raw;
        }
        return raw.substring(start, end + 1);
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
