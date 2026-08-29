package com.legalcs.dao;

import java.util.List;

import com.legalcs.entity.RepaymentRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RepaymentDAO {

    private static final String SELECT_BY_CASE_ID =
            "SELECT case_id, amount, channel, repaid_at FROM repayment_record WHERE case_id = ? ORDER BY repaid_at DESC";

    private final JdbcTemplate businessJdbcTemplate;

    public List<RepaymentRecord> findByCaseId(long caseId) {
        return businessJdbcTemplate.query(SELECT_BY_CASE_ID,
                (rs, rowNum) -> new RepaymentRecord(
                        rs.getLong("case_id"),
                        rs.getBigDecimal("amount"),
                        rs.getString("channel"),
                        rs.getTimestamp("repaid_at").toLocalDateTime()),
                caseId);
    }
}
