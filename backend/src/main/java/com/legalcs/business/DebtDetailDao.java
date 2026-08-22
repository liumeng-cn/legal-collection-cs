package com.legalcs.business;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DebtDetailDao {

    private static final String SELECT_BY_CASE_ID =
            "SELECT case_id, principal, interest, fee FROM debt_detail WHERE case_id = ?";

    private final JdbcTemplate businessJdbcTemplate;

    public Optional<DebtDetail> findByCaseId(long caseId) {
        List<DebtDetail> result = businessJdbcTemplate.query(SELECT_BY_CASE_ID,
                (rs, rowNum) -> new DebtDetail(
                        rs.getLong("case_id"),
                        rs.getBigDecimal("principal"),
                        rs.getBigDecimal("interest"),
                        rs.getBigDecimal("fee")),
                caseId);
        return result.stream().findFirst();
    }
}
