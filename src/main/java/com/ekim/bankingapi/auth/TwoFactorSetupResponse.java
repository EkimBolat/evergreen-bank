package com.ekim.bankingapi.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TwoFactorSetupResponse {

    private String secret;
    private String otpAuthUri;
}
