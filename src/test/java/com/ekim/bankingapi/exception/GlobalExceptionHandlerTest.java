package com.ekim.bankingapi.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNoRouteFound_shouldReturn404_forNoResourceFoundException() {
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/totally-made-up-path");
        NoResourceFoundException ex = new NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, "/api/v1/totally-made-up-path", "/api/v1/totally-made-up-path");

        ResponseEntity<ErrorResponse> response = handler.handleNoRouteFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).contains("/api/v1/totally-made-up-path");
    }

    @Test
    void handleNoRouteFound_shouldReturn404_forNoHandlerFoundException() {
        HttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/also-made-up");
        NoHandlerFoundException ex = new NoHandlerFoundException("POST", "/api/v1/also-made-up", null);

        ResponseEntity<ErrorResponse> response = handler.handleNoRouteFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleGeneric_shouldStillReturn500_forUnrelatedExceptions() {
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/me");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(new RuntimeException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
