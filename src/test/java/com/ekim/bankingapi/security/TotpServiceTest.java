package com.ekim.bankingapi.security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private final TotpService totpService = new TotpService();

    @Test
    void generateCode_shouldMatchRfc6238TestVector() throws Exception {
        // RFC 6238 Appendix B: secret "12345678901234567890", T=59s -> step 1, SHA1 code 94287082 (last 6 digits: 287082)
        byte[] key = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

        Method generateCode = TotpService.class.getDeclaredMethod("generateCode", byte[].class, long.class);
        generateCode.setAccessible(true);

        String code = (String) generateCode.invoke(totpService, key, 1L);

        assertThat(code).isEqualTo("287082");
    }

    @Test
    void generateSecret_shouldReturnBase32EncodedString() {
        String secret = totpService.generateSecret();

        assertThat(secret).isNotBlank();
        assertThat(secret).matches("[A-Z2-7]+");
    }

    @Test
    void verifyCode_shouldReturnTrue_forCurrentlyValidCode() throws Exception {
        String secret = totpService.generateSecret();

        Method base32Decode = TotpService.class.getDeclaredMethod("base32Decode", String.class);
        base32Decode.setAccessible(true);
        byte[] key = (byte[]) base32Decode.invoke(totpService, secret);

        Method generateCode = TotpService.class.getDeclaredMethod("generateCode", byte[].class, long.class);
        generateCode.setAccessible(true);
        long currentStep = Instant.now().getEpochSecond() / 30;
        String currentCode = (String) generateCode.invoke(totpService, key, currentStep);

        assertThat(totpService.verifyCode(secret, currentCode)).isTrue();
    }

    @Test
    void verifyCode_shouldReturnFalse_forWrongCode() {
        String secret = totpService.generateSecret();

        assertThat(totpService.verifyCode(secret, "000000")).isFalse();
    }

    @Test
    void verifyCode_shouldReturnFalse_whenCodeIsNotSixDigits() {
        String secret = totpService.generateSecret();

        assertThat(totpService.verifyCode(secret, "12")).isFalse();
        assertThat(totpService.verifyCode(secret, "abcdef")).isFalse();
    }

    @Test
    void getOtpAuthUri_shouldContainIssuerEmailAndSecret() {
        String uri = totpService.getOtpAuthUri("ahmet@example.com", "SECRETVALUE");

        assertThat(uri).startsWith("otpauth://totp/EvergreenBank:ahmet@example.com");
        assertThat(uri).contains("secret=SECRETVALUE");
        assertThat(uri).contains("issuer=EvergreenBank");
    }
}
