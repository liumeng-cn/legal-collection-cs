package com.legalcs.service.rag;

import com.legalcs.service.auth.AuthContext;
import com.legalcs.dao.BindingDAO;
import com.legalcs.common.Role;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CaseScopeResolver {

    private final BindingDAO bindingDao;

    public List<Long> resolve(AuthContext authContext) {
        if (authContext.getRole() != Role.DEBTOR) {
            return List.of();
        }
        return bindingDao.findCaseIdsByDebtor(Long.parseLong(authContext.getUserId()));
    }
}
