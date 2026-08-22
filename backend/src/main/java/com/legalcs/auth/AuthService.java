package com.legalcs.auth;

import com.legalcs.auth.dto.DebtorVerifyRequest;
import com.legalcs.auth.dto.LoginRequest;
import com.legalcs.auth.dto.TokenResponse;
import com.legalcs.business.CaseDao;
import com.legalcs.common.Role;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final StaffDao staffDao;
    private final DebtorDao debtorDao;
    private final CaseDao caseDao;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public TokenResponse login(LoginRequest request) {
        StaffAccount staff = staffDao.findByUsername(request.username())
                .filter(account -> passwordEncoder.matches(request.password(), account.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));
        return buildToken(staff.getId(), staff.getRole(), staff.getName());
    }

    public TokenResponse verifyDebtor(DebtorVerifyRequest request) {
        DebtorAccount debtor = resolveDebtor(request.identifier())
                .orElseThrow(() -> new BadCredentialsException("案件号或身份证号无效"));
        return buildToken(debtor.getId(), Role.DEBTOR, debtor.getName());
    }

    private TokenResponse buildToken(long userId, Role role, String name) {
        String token = jwtService.generateToken(String.valueOf(userId), role);
        return new TokenResponse(token, role.name(), String.valueOf(userId), name);
    }

    private Optional<DebtorAccount> resolveDebtor(String identifier) {
        Optional<DebtorAccount> byIdCard = debtorDao.findByIdCard(identifier);
        if (byIdCard.isPresent()) {
            return byIdCard;
        }
        return caseDao.findDebtorIdByCaseNo(identifier)
                .flatMap(debtorDao::findById);
    }
}
