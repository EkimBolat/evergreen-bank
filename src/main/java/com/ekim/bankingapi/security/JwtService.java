package com.ekim.bankingapi.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private static final long TWO_FACTOR_PENDING_EXPIRATION_MS = 5 * 60 * 1000;
    private static final String TWO_FACTOR_PENDING_PURPOSE = "2FA_PENDING";

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String email, Long userId, Long customerId, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("customerId", customerId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateTwoFactorPendingToken(String email) {
        return Jwts.builder()
                .subject(email)
                .claim("purpose", TWO_FACTOR_PENDING_PURPOSE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TWO_FACTOR_PENDING_EXPIRATION_MS))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean isTwoFactorPendingToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return TWO_FACTOR_PENDING_PURPOSE.equals(claims.get("purpose", String.class)) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public Long extractCustomerId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("customerId", Long.class);
    }

    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("role", String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}