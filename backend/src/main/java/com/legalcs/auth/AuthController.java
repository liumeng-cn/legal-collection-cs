package com.legalcs.auth;

import com.legalcs.auth.dto.DebtorVerifyRequest;
import com.legalcs.auth.dto.LoginRequest;
import com.legalcs.auth.dto.TokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Mono<ResponseEntity<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return Mono.fromCallable(() -> authService.login(request))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/debtor/verify")
    public Mono<ResponseEntity<TokenResponse>> verifyDebtor(@Valid @RequestBody DebtorVerifyRequest request) {
        return Mono.fromCallable(() -> authService.verifyDebtor(request))
                .map(ResponseEntity::ok);
    }
}
