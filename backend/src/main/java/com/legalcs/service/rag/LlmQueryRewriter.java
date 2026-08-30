package com.legalcs.service.rag;

import com.legalcs.client.DeepSeekClient;
import com.legalcs.dto.CompletionMessage;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmQueryRewriter implements QueryRewriter {

    private static final String REWRITE_SYSTEM_PROMPT = """
            你是查询改写助手。将用户问题改写为 2~3 个适合知识库检索的独立查询，每个查询占一行。
            要求：保持用户原始意图，不要编造问题中不存在的事实；只输出查询本身，不要编号、不要解释。
            """;
    private static final double REWRITE_TEMPERATURE = 0.3;
    private static final int MAX_TOTAL_QUERIES = 4;

    private final DeepSeekClient deepSeekClient;

    @Override
    public List<String> rewrite(String query) {
        List<String> queries = new ArrayList<>();
        queries.add(query);
        try {
            String content = callModel(query);
            for (String subquery : parseSubqueries(content)) {
                if (queries.size() >= MAX_TOTAL_QUERIES) {
                    break;
                }
                if (!queries.contains(subquery)) {
                    queries.add(subquery);
                }
            }
        } catch (RuntimeException e) {
            log.warn("查询改写失败，退回原始问题: {}", e.getMessage());
        }
        return queries;
    }

    private String callModel(String query) {
        List<CompletionMessage> messages = List.of(
                new CompletionMessage("system", REWRITE_SYSTEM_PROMPT),
                new CompletionMessage("user", query));
        return deepSeekClient.chat(messages, REWRITE_TEMPERATURE);
    }

    private List<String> parseSubqueries(String content) {
        List<String> result = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return result;
        }
        for (String line : content.split("\\R")) {
            String subquery = stripNumbering(line.trim());
            if (!subquery.isEmpty()) {
                result.add(subquery);
            }
        }
        return result;
    }

    private String stripNumbering(String line) {
        return line.replaceFirst("^\\s*(?:\\d+[.、)]|[-*•])\\s*", "").trim();
    }
}
