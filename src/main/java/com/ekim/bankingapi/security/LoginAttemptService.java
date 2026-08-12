package com.ekim.bankingapi.security;

import com.ekim.bankingapi.exception.AccountLockedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 15;

    private final ConcurrentHashMap<String, AtomicInteger> attemptCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lockedUntil = new ConcurrentHashMap<>();

    public void checkNotLocked(String nationalId) {
        Instant lockExpiry = lockedUntil.get(nationalId);
        if (lockExpiry != null) {
            if (Instant.now().isBefore(lockExpiry)) {
                long minutesLeft = (lockExpiry.getEpochSecond() - Instant.now().getEpochSecond()) / 60 + 1;
                throw new AccountLockedException(
                        "Too many failed login attempts. Please try again in " + minutesLeft + " minute(s).");
            } else {
                lockedUntil.remove(nationalId);
                attemptCounts.remove(nationalId);
            }
        }
    }

    public boolean recordFailedAttempt(String nationalId) {
        int attempts = attemptCounts.computeIfAbsent(nationalId, k -> new AtomicInteger(0)).incrementAndGet();

        if (attempts >= MAX_ATTEMPTS) {
            lockedUntil.put(nationalId, Instant.now().plusSeconds(LOCK_DURATION_MINUTES * 60));
            log.warn("Account locked due to too many failed attempts: nationalId={}", nationalId);
            return true;
        }
        return false;
    }

    public void recordSuccessfulLogin(String nationalId) {
        attemptCounts.remove(nationalId);
        lockedUntil.remove(nationalId);
    }
}