package com.legalcs.entity;

import com.legalcs.common.Role;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class StaffAccount {

    private final long id;
    private final String username;
    private final String passwordHash;
    private final String name;
    private final Role role;
}
