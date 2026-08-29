package com.legalcs.entity;

import com.legalcs.common.CaseStatus;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CaseInfo {

    private final long id;
    private final String caseNo;
    private final long debtorId;
    private final CaseStatus status;
    private final BigDecimal amountTotal;
}
