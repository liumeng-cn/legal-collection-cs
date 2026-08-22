package com.legalcs.auth;

import com.legalcs.common.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StaffAccountInitializer implements ApplicationRunner {

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin123";
    private static final String DEFAULT_NAME = "催收管理员";
    private static final String SRE_USERNAME = "sre";
    private static final String SRE_PASSWORD = "sre123456";
    private static final String SRE_NAME = "排障运维";

    private final StaffDao staffDao;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        ensureStaff(DEFAULT_USERNAME, DEFAULT_PASSWORD, DEFAULT_NAME, Role.STAFF);
        ensureStaff(SRE_USERNAME, SRE_PASSWORD, SRE_NAME, Role.SRE);
    }

    private void ensureStaff(String username, String password, String name, Role role) {
        if (staffDao.findByUsername(username).isEmpty()) {
            staffDao.insert(username, passwordEncoder.encode(password), name, role);
        }
    }
}
