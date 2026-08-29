package com.legalcs.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.legalcs.service.auth.AuthContext;
import com.legalcs.dao.BindingDAO;
import com.legalcs.common.Role;
import com.legalcs.dao.DocumentDAO;
import com.legalcs.entity.RagChunk;
import com.legalcs.service.rag.CaseScopeResolver;
import com.legalcs.service.rag.RagPipeline;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagRetrievalToolTest {

    private static final AuthContext STAFF = new AuthContext("100", Role.STAFF);

    @Test
    void searchKnowledge_emptyReturnsSignal() {
        RagPipeline ragPipeline = mock(RagPipeline.class);
        when(ragPipeline.retrieve(anyString(), any())).thenReturn(List.of());
        RagRetrievalTool tool = tool(ragPipeline, mock(DocumentDAO.class));

        assertEquals("[无检索结果]", tool.searchKnowledge("测试", STAFF));
    }

    @Test
    void searchKnowledge_formatsChunks() {
        RagPipeline ragPipeline = mock(RagPipeline.class);
        when(ragPipeline.retrieve(anyString(), any()))
                .thenReturn(List.of(new RagChunk(1L, 9L, 0, "标题", "正文", 0.95)));
        RagRetrievalTool tool = tool(ragPipeline, mock(DocumentDAO.class));

        String result = tool.searchKnowledge("测试", STAFF);
        assertTrue(result.contains("【标题】正文"));
        assertTrue(result.contains("相似度 0.950"));
        assertTrue(result.contains("doc_id=9"));
    }

    @Test
    void expandKnowledge_fullDocNullReturnsNoContext() {
        DocumentDAO documentDao = mock(DocumentDAO.class);
        when(documentDao.expandFull(anyLong(), anyString(), anyList())).thenReturn(null);
        RagRetrievalTool tool = tool(mock(RagPipeline.class), documentDao);

        assertEquals("（无更多上下文）", tool.expandKnowledge(9L, 0, -1, STAFF));
    }

    @Test
    void expandKnowledge_neighborsEmptyReturnsNoContext() {
        DocumentDAO documentDao = mock(DocumentDAO.class);
        when(documentDao.expandNeighbors(anyLong(), anyInt(), anyInt(), anyString(), anyList()))
                .thenReturn(List.of());
        RagRetrievalTool tool = tool(mock(RagPipeline.class), documentDao);

        assertEquals("（无更多上下文）", tool.expandKnowledge(9L, 0, 2, STAFF));
    }

    @Test
    void toPgArray_emptyAndNonEmpty() {
        assertEquals("{}", DocumentDAO.toPgArray(List.of()));
        assertEquals("{1,2,3}", DocumentDAO.toPgArray(List.of(1L, 2L, 3L)));
    }

    private RagRetrievalTool tool(RagPipeline ragPipeline, DocumentDAO documentDao) {
        return new RagRetrievalTool(ragPipeline, documentDao, new CaseScopeResolver(mock(BindingDAO.class)));
    }
}
