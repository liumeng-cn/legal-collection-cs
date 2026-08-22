package com.legalcs.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.legalcs.auth.AuthContext;
import com.legalcs.auth.BindingDao;
import com.legalcs.common.Role;
import com.legalcs.knowledge.DocumentDao;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagPermissionTest {

    @Test
    void resolveCaseIds_staffReturnsEmpty() {
        RagRetrievalTool tool = tool(mock(BindingDao.class));
        assertEquals(List.of(), tool.resolveCaseIds(new AuthContext("100", Role.STAFF)));
    }

    @Test
    void resolveCaseIds_sreReturnsEmpty() {
        RagRetrievalTool tool = tool(mock(BindingDao.class));
        assertEquals(List.of(), tool.resolveCaseIds(new AuthContext("200", Role.SRE)));
    }

    @Test
    void resolveCaseIds_debtorReturnsBindings() {
        BindingDao bindingDao = mock(BindingDao.class);
        when(bindingDao.findCaseIdsByDebtor(1L)).thenReturn(List.of(1L, 2L));
        RagRetrievalTool tool = tool(bindingDao);
        assertEquals(List.of(1L, 2L), tool.resolveCaseIds(new AuthContext("1", Role.DEBTOR)));
    }

    @Test
    void toPgArray_emptyAndNonEmpty() {
        assertEquals("{}", DocumentDao.toPgArray(List.of()));
        assertEquals("{1,2,3}", DocumentDao.toPgArray(List.of(1L, 2L, 3L)));
    }

    private RagRetrievalTool tool(BindingDao bindingDao) {
        return new RagRetrievalTool(null, null, bindingDao);
    }
}
