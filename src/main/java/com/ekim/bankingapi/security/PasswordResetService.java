package com.ekim.bankingapi.security;

import com.ekim.bankingapi.auth.User;
import com.ekim.bankingapi.exception.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final long RESET_TOKEN_VALIDITY_MINUTES = 30;

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Transactional
    public String createResetToken(User user) {
        passwordResetTokenRepository.deleteByUserId(user.getId());

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(UUID.randomUUID().toString());
        resetToken.setUser(user);
        resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(RESET_TOKEN_VALIDITY_MINUTES));

        PasswordResetToken saved = passwordResetTokenRepository.save(resetToken);
        return saved.getToken();
    }

    @Transactional
    public User validateAndConsumeToken(String token) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired password reset link"));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            passwordResetTokenRepository.delete(resetToken);
            throw new InvalidCredentialsException("Invalid or expired password reset link");
        }

        User user = resetToken.getUser();
        // Single-use: consumed immediately so the same link can't be replayed.
        passwordResetTokenRepository.delete(resetToken);
        return user;
    }
}
