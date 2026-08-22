package com.legalcs.auth;

import com.legalcs.common.Role;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AuthContext {

    private final String userId;
    private final Role role;

    public boolean canAccessCase(long caseDebtorId) {
        return role == Role.STAFF || Long.parseLong(userId) == caseDebtorId;
    }
}
