package com.legalcs.auth;

import com.legalcs.common.Role;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StaffDao {

    private static final String SELECT_BY_USERNAME =
            "SELECT id, username, password_hash, name, role FROM staff WHERE username = ?";
    private static final String INSERT_STAFF =
            "INSERT INTO staff (username, password_hash, name, role) VALUES (?, ?, ?, ?)";

    private final JdbcTemplate authJdbcTemplate;

    public Optional<StaffAccount> findByUsername(String username) {
        List<StaffAccount> result = authJdbcTemplate.query(SELECT_BY_USERNAME,
                (rs, rowNum) -> new StaffAccount(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("name"),
                        Role.valueOf(rs.getString("role"))),
                username);
        return result.stream().findFirst();
    }

    public void insert(String username, String passwordHash, String name, Role role) {
        authJdbcTemplate.update(INSERT_STAFF, username, passwordHash, name, role.name());
    }
}
