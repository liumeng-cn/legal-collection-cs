package com.legalcs.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class DebtorAccount {

    private final long id;
    private final String name;
    private final String idCard;
    private final String phone;
}
