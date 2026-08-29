package com.legalcs.service.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.legalcs.service.auth.AuthContext;
import com.legalcs.dao.BindingDAO;
import com.legalcs.common.Role;
import java.util.List;

import org.junit.jupiter.api.Test;

class CaseScopeResolverTest {

    @Test
    void staffAndSreReturnEmpty() {
        CaseScopeResolver resolver = new CaseScopeResolver(mock(BindingDAO.class));
        assertEquals(List.of(), resolver.resolve(new AuthContext("100", Role.STAFF)));
        assertEquals(List.of(), resolver.resolve(new AuthContext("200", Role.SRE)));
    }

    @Test
    void debtorReturnsBindings() {
        BindingDAO bindingDao = mock(BindingDAO.class);
        when(bindingDao.findCaseIdsByDebtor(1L)).thenReturn(List.of(1L, 2L));
        CaseScopeResolver resolver = new CaseScopeResolver(bindingDao);
        assertEquals(List.of(1L, 2L), resolver.resolve(new AuthContext("1", Role.DEBTOR)));
    }
}
