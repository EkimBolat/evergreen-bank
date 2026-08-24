package com.ekim.bankingapi.idempotency;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyServiceTest {

    private final IdempotencyService idempotencyService = new IdempotencyService();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCachedResponse_shouldReturnStoredResponse_forSameCustomerAndKey() {
        authenticateAs(1L);
        idempotencyService.storeResponse("key-1", "response-for-customer-1");

        assertThat(idempotencyService.getCachedResponse("key-1")).contains("response-for-customer-1");
    }

    @Test
    void getCachedResponse_shouldNotLeakAcrossCustomers_whenSameKeyReused() {
        authenticateAs(1L);
        idempotencyService.storeResponse("shared-key", "response-for-customer-1");

        authenticateAs(2L);

        assertThat(idempotencyService.getCachedResponse("shared-key")).isEmpty();
    }

    private void authenticateAs(Long customerId) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken("ahmet@example.com", null, List.of());
        authToken.setDetails(customerId);
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
