package com.ekim.bankingapi.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthResponse {

    private Long userId;
    private Long customerId;
    private String email;
    private String message;
    private String token;
    private Role role;
    private String refreshToken;
    private boolean twoFactorRequired;
    private String pendingToken;

    public static AuthResponse twoFactorRequired(String pendingToken) {
        return new AuthResponse(null, null, null, "Two-factor authentication code required", null, null, null, true, pendingToken);
    }
}