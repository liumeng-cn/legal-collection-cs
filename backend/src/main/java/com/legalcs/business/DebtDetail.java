package com.legalcs.business;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class DebtDetail {

    private final long caseId;
    private final BigDecimal principal;
    private final BigDecimal interest;
    private final BigDecimal fee;
}
