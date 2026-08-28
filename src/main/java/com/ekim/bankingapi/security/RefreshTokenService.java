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
public class RefreshTokenService {

    private static final long REFRESH_TOKEN_VALIDITY_DAYS = 7;

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public String createRefreshToken(User user) {
        refreshTokenRepository.deleteByUserId(user.getId());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(LocalDateTime.now().plusDays(REFRESH_TOKEN_VALIDITY_DAYS));

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        return saved.getToken();
    }

    @Transactional
    public void revokeForUser(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    public User validateAndGetUser(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        if (refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new InvalidCredentialsException("Refresh token has expired, please login again");
        }

        return refreshToken.getUser();
    }
}