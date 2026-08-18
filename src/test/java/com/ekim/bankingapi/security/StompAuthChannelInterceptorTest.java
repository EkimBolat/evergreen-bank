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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock
    private JwtService jwtService;

    private final MessageChannel channel = mock(MessageChannel.class);

    private StompAuthChannelInterceptor interceptor;

    @Test
    void preSend_shouldAttachPrincipal_whenConnectHasValidToken() {
        interceptor = new StompAuthChannelInterceptor(jwtService);

        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractEmail("valid-token")).thenReturn("ahmet@example.com");
        when(jwtService.extractRole("valid-token")).thenReturn("CUSTOMER");

        Message<?> message = connectMessage("Bearer valid-token");

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(resultAccessor).isNotNull();
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("ahmet@example.com");
    }

    @Test
    void preSend_shouldThrow_whenConnectHasInvalidToken() {
        interceptor = new StompAuthChannelInterceptor(jwtService);

        when(jwtService.isTokenValid("bad-token")).thenReturn(false);

        Message<?> message = connectMessage("Bearer bad-token");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void preSend_shouldThrow_whenConnectHasTwoFactorPendingToken() {
        interceptor = new StompAuthChannelInterceptor(jwtService);

        when(jwtService.isTokenValid("pending-token")).thenReturn(true);
        when(jwtService.isTwoFactorPendingToken("pending-token")).thenReturn(true);

        Message<?> message = connectMessage("Bearer pending-token");

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void preSend_shouldThrow_whenConnectHasNoAuthorizationHeader() {
        interceptor = new StompAuthChannelInterceptor(jwtService);

        Message<?> message = connectMessage(null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void preSend_shouldPassThrough_whenCommandIsNotConnect() {
        interceptor = new StompAuthChannelInterceptor(jwtService);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    private Message<?> connectMessage(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        if (authorizationHeader != null) {
            accessor.addNativeHeader("Authorization", authorizationHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
