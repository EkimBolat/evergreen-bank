package com.ekim.bankingapi.idempotency;

import com.ekim.bankingapi.exception.InvalidCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdempotencyService {

    private final ConcurrentHashMap<String, Object> processedRequests = new ConcurrentHashMap<>();

    public Optional<Object> getCachedResponse(String idempotencyKey) {
        return Optional.ofNullable(processedRequests.get(scopedKey(idempotencyKey)));
    }

    public void storeResponse(String idempotencyKey, Object response) {
        processedRequests.put(scopedKey(idempotencyKey), response);
    }

    // Scoped per customer so two customers reusing the same client-supplied key never
    // see each other's cached response (which would also leak the other's account details).
    private String scopedKey(String idempotencyKey) {
        return currentCustomerId() + ":" + idempotencyKey;
    }

    private Long currentCustomerId() {
        Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        if (!(details instanceof Long customerId)) {
            throw new InvalidCredentialsException("Unable to resolve authenticated customer");
        }
        return customerId;
    }
}