package com.legalcs.auth;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DebtorDao {

    private static final String SELECT_BY_ID_CARD =
            "SELECT id, name, id_card, phone FROM debtor WHERE id_card = ?";
    private static final String SELECT_BY_ID =
            "SELECT id, name, id_card, phone FROM debtor WHERE id = ?";

    private final JdbcTemplate authJdbcTemplate;

    public Optional<DebtorAccount> findByIdCard(String idCard) {
        List<DebtorAccount> result = authJdbcTemplate.query(SELECT_BY_ID_CARD,
                (rs, rowNum) -> new DebtorAccount(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("id_card"),
                        rs.getString("phone")),
                idCard);
        return result.stream().findFirst();
    }

    public Optional<DebtorAccount> findById(long id) {
        List<DebtorAccount> result = authJdbcTemplate.query(SELECT_BY_ID,
                (rs, rowNum) -> new DebtorAccount(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("id_card"),
                        rs.getString("phone")),
                id);
        return result.stream().findFirst();
    }
}
