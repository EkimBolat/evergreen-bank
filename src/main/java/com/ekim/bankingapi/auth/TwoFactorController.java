package com.ekim.bankingapi.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/2fa")
@RequiredArgsConstructor
public class TwoFactorController {

    private final AuthService authService;

    @PostMapping("/setup")
    public ResponseEntity<TwoFactorSetupResponse> setup() {
        return ResponseEntity.ok(authService.setupTwoFactor());
    }

    @PostMapping("/enable")
    public ResponseEntity<Void> enable(@Valid @RequestBody TwoFactorCodeRequest request) {
        authService.enableTwoFactor(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@Valid @RequestBody TwoFactorCodeRequest request) {
        authService.disableTwoFactor(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthResponse> verify(@Valid @RequestBody TwoFactorVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyTwoFactor(request));
    }
}
