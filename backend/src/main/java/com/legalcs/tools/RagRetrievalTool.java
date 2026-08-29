package com.legalcs.tools;

import com.legalcs.service.auth.AuthContext;
import com.legalcs.dao.DocumentDAO;
import com.legalcs.entity.RagChunk;
import com.legalcs.service.rag.CaseScopeResolver;
import com.legalcs.service.rag.RagPipeline;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagRetrievalTool {

    private static final int FULL_DOCUMENT_WINDOW = -1;
    private static final int MAX_WINDOW = 10;
    private static final int MAX_FULL_CONTENT_LENGTH = 4000;
    private static final String NO_RESULT_SIGNAL = "[无检索结果]";
    private static final String NO_CONTEXT_AVAILABLE = "（无更多上下文）";
    private static final String TRUNCATED_SUFFIX = "\n（内容过长，已截断）";

    private final RagPipeline ragPipeline;
    private final DocumentDAO documentDao;
    private final CaseScopeResolver caseScopeResolver;

    @Tool(name = "search_knowledge", description = "检索法催平台知识库，返回与问题最相关且当前用户有权查看的 FAQ 片段，并附带相似度分数与切片坐标")
    public String searchKnowledge(
            @ToolParam(name = "query", description = "检索问题") String query,
            AuthContext authContext) {
        long start = System.nanoTime();
        List<RagChunk> chunks = ragPipeline.retrieve(query, authContext);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        if (chunks.isEmpty()) {
            log.info("检索空召回 query={} role={} hitCount=0 elapsedMs={}", query, authContext.getRole(), elapsedMs);
            return NO_RESULT_SIGNAL;
        }
        log.info("检索命中 query={} role={} hitCount={} elapsedMs={}", query, authContext.getRole(), chunks.size(), elapsedMs);
        chunks.forEach(chunk -> log.info("检索明细 docId={} chunkIndex={} score={} title={}",
                chunk.getDocumentId(), chunk.getChunkIndex(), String.format("%.3f", chunk.getScore()), chunk.getTitle()));
        return chunks.stream()
                .map(this::formatChunk)
                .collect(Collectors.joining("\n"));
    }

    @Tool(name = "expand_knowledge", description = "展开知识内容：window=-1 返回整篇文档，window>=0 返回命中切片前后 window 片相邻切片；结果同样受当前用户权限过滤")
    public String expandKnowledge(
            @ToolParam(name = "document_id", description = "文档 id") long documentId,
            @ToolParam(name = "chunk_index", description = "命中切片序号") int chunkIndex,
            @ToolParam(name = "window", description = "扩展窗口：-1 整篇，>=0 相邻片数") int window,
            AuthContext authContext) {
        String role = authContext.getRole().name();
        List<Long> caseIds = caseScopeResolver.resolve(authContext);
        if (window == FULL_DOCUMENT_WINDOW) {
            String full = documentDao.expandFull(documentId, role, caseIds);
            return full == null ? NO_CONTEXT_AVAILABLE : truncate(full);
        }
        int boundedWindow = Math.min(Math.max(window, 0), MAX_WINDOW);
        List<String> neighbors = documentDao.expandNeighbors(documentId, chunkIndex, boundedWindow, role, caseIds);
        if (neighbors.isEmpty()) {
            return NO_CONTEXT_AVAILABLE;
        }
        return String.join("\n", neighbors);
    }

    private String formatChunk(RagChunk chunk) {
        return "【" + chunk.getTitle() + "】" + chunk.getText()
                + "（相似度 " + String.format("%.3f", chunk.getScore())
                + "｜doc_id=" + chunk.getDocumentId()
                + "｜chunk=" + chunk.getChunkIndex() + "）";
    }

    private String truncate(String content) {
        if (content.length() <= MAX_FULL_CONTENT_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_FULL_CONTENT_LENGTH) + TRUNCATED_SUFFIX;
    }
}
