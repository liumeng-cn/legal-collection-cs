package com.legalcs.dao;

import com.legalcs.common.CaseStatus;
import java.util.List;
import java.util.Optional;

import com.legalcs.entity.CaseInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CaseDAO {

    private static final String SELECT_DEBTOR_ID_BY_CASE_NO =
            "SELECT debtor_id FROM case_info WHERE case_no = ?";
    private static final String SELECT_BY_CASE_NO =
            "SELECT id, case_no, debtor_id, status, amount_total FROM case_info WHERE case_no = ?";
    private static final String SELECT_BY_DEBTOR_ID =
            "SELECT id, case_no, debtor_id, status, amount_total FROM case_info WHERE debtor_id = ?";
    private static final String SELECT_ALL =
            "SELECT id, case_no, debtor_id, status, amount_total FROM case_info";

    private final JdbcTemplate businessJdbcTemplate;

    public Optional<Long> findDebtorIdByCaseNo(String caseNo) {
        List<Long> result = businessJdbcTemplate.query(SELECT_DEBTOR_ID_BY_CASE_NO,
                (rs, rowNum) -> rs.getLong("debtor_id"), caseNo);
        return result.stream().findFirst();
    }

    public Optional<CaseInfo> findByCaseNo(String caseNo) {
        List<CaseInfo> result = businessJdbcTemplate.query(SELECT_BY_CASE_NO,
                (rs, rowNum) -> new CaseInfo(
                        rs.getLong("id"),
                        rs.getString("case_no"),
                        rs.getLong("debtor_id"),
                        CaseStatus.valueOf(rs.getString("status")),
                        rs.getBigDecimal("amount_total")),
                caseNo);
        return result.stream().findFirst();
    }

    public List<CaseInfo> findByDebtorId(long debtorId) {
        return businessJdbcTemplate.query(SELECT_BY_DEBTOR_ID,
                (rs, rowNum) -> new CaseInfo(
                        rs.getLong("id"),
                        rs.getString("case_no"),
                        rs.getLong("debtor_id"),
                        CaseStatus.valueOf(rs.getString("status")),
                        rs.getBigDecimal("amount_total")),
                debtorId);
    }

    public List<CaseInfo> findAll() {
        return businessJdbcTemplate.query(SELECT_ALL,
                (rs, rowNum) -> new CaseInfo(
                        rs.getLong("id"),
                        rs.getString("case_no"),
                        rs.getLong("debtor_id"),
                        CaseStatus.valueOf(rs.getString("status")),
                        rs.getBigDecimal("amount_total")));
    }
}
