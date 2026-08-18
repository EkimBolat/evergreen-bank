package com.ekim.bankingapi.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Authenticates STOMP CONNECT frames and re-checks the token's expiry on every
 * subsequent SEND/SUBSCRIBE frame of that session, since the client only presents
 * the JWT once at CONNECT time — without this, a socket opened before token expiry
 * would otherwise stay usable indefinitely, unlike the HTTP path where every
 * request is re-validated by JwtAuthFilter.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String EXPIRES_AT_ATTRIBUTE = "jwtExpiresAt";

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            authenticateConnect(accessor);
        } else if (StompCommand.SEND.equals(command) || StompCommand.SUBSCRIBE.equals(command)) {
            rejectIfExpired(accessor);
        }

        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        String token = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7)
                : null;

        if (token == null || !jwtService.isTokenValid(token) || jwtService.isTwoFactorPendingToken(token)) {
            throw new BadCredentialsException("Invalid or missing WebSocket authentication token");
        }

        String email = jwtService.extractEmail(token);
        String role = jwtService.extractRole(token);
        Long customerId = jwtService.extractCustomerId(token);

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        authToken.setDetails(customerId);
        accessor.setUser(authToken);

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            sessionAttributes.put(EXPIRES_AT_ATTRIBUTE, jwtService.extractExpiration(token));
        }
    }

    private void rejectIfExpired(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        Object expiresAt = sessionAttributes == null ? null : sessionAttributes.get(EXPIRES_AT_ATTRIBUTE);

        if (expiresAt instanceof Date expiry && expiry.before(new Date())) {
            throw new BadCredentialsException("WebSocket session token has expired");
        }
    }
}
