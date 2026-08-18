package com.ekim.bankingapi.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    private static final String EXPIRES_AT_ATTRIBUTE = "jwtExpiresAt";

    @Mock
    private JwtService jwtService;

    private final MessageChannel channel = mock(MessageChannel.class);

    private StompAuthChannelInterceptor interceptor;

    @Test
    void preSend_shouldAttachPrincipalAndStoreExpiry_whenConnectHasValidToken() {
        interceptor = new StompAuthChannelInterceptor(jwtService);

        Date expiry = new Date(System.currentTimeMillis() + 60_000);
        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractEmail("valid-token")).thenReturn("ahmet@example.com");
        when(jwtService.extractRole("valid-token")).thenReturn("CUSTOMER");
        when(jwtService.extractCustomerId("valid-token")).thenReturn(1L);
        when(jwtService.extractExpiration("valid-token")).thenReturn(expiry);

        Map<String, Object> sessionAttributes = new HashMap<>();
        Message<?> message = connectMessage("Bearer valid-token", sessionAttributes);

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("ahmet@example.com");
        assertThat(sessionAttributes.get(EXPIRES_AT_ATTRIBUTE)).isEqualTo(expiry);
    }

    @Test
    void preSend_shouldThrow_whenConnectHasInvalidToken() {
        interceptor = new StompAuthChannelInterceptor(jwtService);

        when(jwtService.isTokenValid("bad-token")).thenReturn(false);

        Message<?> message = connectMessage("Bearer bad-token", new HashMap<>());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void preSend_shouldThrow_whenConnectHasTwoFactorPendingToken() {
        interceptor = new StompAuthChannelInterceptor(jwtService);

        when(jwtService.isTokenValid("pending-token")).thenReturn(true);
        when(jwtService.isTwoFactorPendingToken("pending-token")).thenReturn(true);

        Message<?> message = connectMessage("Bearer pending-token", new HashMap<>());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void preSend_shouldThrow_whenConnectHasNoAuthorizationHeader() {
        interceptor = new StompAuthChannelInterceptor(jwtService);

        Message<?> message = connectMessage(null, new HashMap<>());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void preSend_shouldThrow_whenSubsequentSendFrameUsesExpiredSessionToken() {
        interceptor = new StompAuthChannelInterceptor(jwtService);

        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(EXPIRES_AT_ATTRIBUTE, new Date(System.currentTimeMillis() - 1000));

        Message<?> message = frameMessage(StompCommand.SEND, sessionAttributes);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void preSend_shouldThrow_whenSubscribeFrameUsesExpiredSessionToken() {
        interceptor = new StompAuthChannelInterceptor(jwtService);

        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(EXPIRES_AT_ATTRIBUTE, new Date(System.currentTimeMillis() - 1000));

        Message<?> message = frameMessage(StompCommand.SUBSCRIBE, sessionAttributes);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void preSend_shouldPassThrough_whenSendFrameUsesNonExpiredSessionToken() {
        interceptor = new StompAuthChannelInterceptor(jwtService);

        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(EXPIRES_AT_ATTRIBUTE, new Date(System.currentTimeMillis() + 60_000));

        Message<?> message = frameMessage(StompCommand.SEND, sessionAttributes);

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    @Test
    void preSend_shouldPassThrough_whenSendFrameHasNoExpiryRecorded() {
        interceptor = new StompAuthChannelInterceptor(jwtService);

        Message<?> message = frameMessage(StompCommand.SEND, null);

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    @Test
    void preSend_shouldPassThrough_whenCommandIsDisconnect() {
        interceptor = new StompAuthChannelInterceptor(jwtService);

        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(EXPIRES_AT_ATTRIBUTE, new Date(System.currentTimeMillis() - 1000));

        Message<?> message = frameMessage(StompCommand.DISCONNECT, sessionAttributes);

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    private Message<?> connectMessage(String authorizationHeader, Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setSessionAttributes(sessionAttributes);
        if (authorizationHeader != null) {
            accessor.addNativeHeader("Authorization", authorizationHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> frameMessage(StompCommand command, Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (sessionAttributes != null) {
            accessor.setSessionAttributes(sessionAttributes);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
