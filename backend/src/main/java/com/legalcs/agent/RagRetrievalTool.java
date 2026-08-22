package com.legalcs.agent;

import com.legalcs.auth.AuthContext;
import com.legalcs.auth.BindingDao;
import com.legalcs.common.Role;
import com.legalcs.knowledge.DocumentDao;
import com.legalcs.knowledge.EmbeddingClient;
import com.legalcs.knowledge.RagChunk;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RagRetrievalTool {

    private static final int TOP_K = 3;
    private static final String EMPTY_RESULT = "知识库暂无相关内容";

    private final DocumentDao documentDao;
    private final EmbeddingClient embeddingClient;
    private final BindingDao bindingDao;

    @Tool(name = "search_knowledge", description = "检索法催平台知识库，返回与问题最相关且当前用户有权查看的 FAQ 片段，并附带相似度分数")
    public String searchKnowledge(
            @ToolParam(name = "query", description = "检索问题") String query,
            AuthContext authContext) {
        float[] embedding = embeddingClient.embed(query);
        List<RagChunk> chunks = documentDao.search(embedding, TOP_K,
                authContext.getRole().name(), resolveCaseIds(authContext));
        if (chunks.isEmpty()) {
            return EMPTY_RESULT;
        }
        return chunks.stream()
                .map(this::formatChunk)
                .collect(Collectors.joining("\n"));
    }

    List<Long> resolveCaseIds(AuthContext authContext) {
        if (authContext.getRole() != Role.DEBTOR) {
            return List.of();
        }
        return bindingDao.findCaseIdsByDebtor(Long.parseLong(authContext.getUserId()));
    }

    private String formatChunk(RagChunk chunk) {
        return "【" + chunk.getTitle() + "】" + chunk.getText()
                + "（相似度 " + String.format("%.3f", chunk.getScore()) + "）";
    }
}
