package com.ekim.bankingapi.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Demo-mode response: no email service is configured, so the reset token is returned
 * directly instead of being emailed. A real deployment would email a link containing this
 * token and never return it in the API response.
 */
@Getter
@AllArgsConstructor
public class ForgotPasswordResponse {

    private String resetToken;
}
