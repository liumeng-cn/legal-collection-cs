package com.legalcs.dao;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BindingDAO {

    private static final String SELECT_CASE_IDS_BY_DEBTOR =
            "SELECT case_id FROM debtor_case_binding WHERE debtor_id = ?";
    private static final String SELECT_DEBTOR_ID_BY_CASE =
            "SELECT debtor_id FROM debtor_case_binding WHERE case_id = ?";

    private final JdbcTemplate authJdbcTemplate;

    public List<Long> findCaseIdsByDebtor(long debtorId) {
        return authJdbcTemplate.query(SELECT_CASE_IDS_BY_DEBTOR,
                (rs, rowNum) -> rs.getLong("case_id"), debtorId);
    }

    public Optional<Long> findDebtorIdByCase(long caseId) {
        List<Long> result = authJdbcTemplate.query(SELECT_DEBTOR_ID_BY_CASE,
                (rs, rowNum) -> rs.getLong("debtor_id"), caseId);
        return result.stream().findFirst();
    }
}
