package com.legalcs.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class RepaymentRecord {

    private final long caseId;
    private final BigDecimal amount;
    private final String channel;
    private final LocalDateTime repaidAt;
}
