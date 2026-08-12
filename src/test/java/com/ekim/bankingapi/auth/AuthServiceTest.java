package com.ekim.bankingapi.auth;

import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.customer.Customer;
import com.ekim.bankingapi.customer.CustomerService;
import com.ekim.bankingapi.exception.DuplicateResourceException;
import com.ekim.bankingapi.exception.InvalidCredentialsException;
import com.ekim.bankingapi.exception.ResourceNotFoundException;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.security.JwtService;
import com.ekim.bankingapi.security.LoginAttemptService;
import com.ekim.bankingapi.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AuthService authService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId(1L);
        customer.setNationalId("12345678901");
    }

    @Test
    void register_shouldSucceed_whenDataIsValid() {
        RegisterRequest request = new RegisterRequest();
        request.setNationalId("12345678901");
        request.setEmail("ahmet@example.com");
        request.setPassword("plain-password");

        when(customerService.findCustomerEntityByNationalId("12345678901")).thenReturn(customer);
        when(userRepository.existsByCustomerId(1L)).thenReturn(false);
        when(userRepository.existsByEmail("ahmet@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtService.generateToken(anyString(), anyLong(), anyLong(), anyString())).thenReturn("fake-jwt-token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("fake-refresh-token");

        AuthResponse response = authService.register(request);

        assertThat(response.getToken()).isEqualTo("fake-jwt-token");
        assertThat(response.getRefreshToken()).isEqualTo("fake-refresh-token");
        assertThat(response.getEmail()).isEqualTo("ahmet@example.com");
        verify(passwordEncoder).encode("plain-password");
    }

    @Test
    void register_shouldThrow_whenCustomerAlreadyHasAccount() {
        RegisterRequest request = new RegisterRequest();
        request.setNationalId("12345678901");
        request.setEmail("ahmet@example.com");
        request.setPassword("plain-password");

        when(customerService.findCustomerEntityByNationalId("12345678901")).thenReturn(customer);
        when(userRepository.existsByCustomerId(1L)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void login_shouldSucceed_whenCredentialsAreCorrect() {
        LoginRequest request = new LoginRequest();
        request.setNationalId("12345678901");
        request.setPassword("plain-password");

        User user = new User();
        user.setId(1L);
        user.setEmail("ahmet@example.com");
        user.setPassword("hashed-password");
        user.setCustomer(customer);

        when(customerService.findCustomerEntityByNationalId("12345678901")).thenReturn(customer);
        when(userRepository.findByCustomerId(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(anyString(), anyLong(), anyLong(), anyString())).thenReturn("fake-jwt-token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("fake-refresh-token");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("fake-jwt-token");
        assertThat(response.getRefreshToken()).isEqualTo("fake-refresh-token");
        assertThat(response.getMessage()).isEqualTo("Login successful");
        verify(loginAttemptService).recordSuccessfulLogin("12345678901");
    }

    @Test
    void login_shouldThrow_whenPasswordIsWrong() {
        LoginRequest request = new LoginRequest();
        request.setNationalId("12345678901");
        request.setPassword("wrong-password");

        User user = new User();
        user.setEmail("ahmet@example.com");
        user.setPassword("hashed-password");
        user.setCustomer(customer);

        when(customerService.findCustomerEntityByNationalId("12345678901")).thenReturn(customer);
        when(userRepository.findByCustomerId(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptService).recordFailedAttempt("12345678901");
    }

    @Test
    void login_shouldThrow_whenNationalIdNotFound() {
        LoginRequest request = new LoginRequest();
        request.setNationalId("00000000000");
        request.setPassword("123456");

        when(customerService.findCustomerEntityByNationalId("00000000000"))
                .thenThrow(new ResourceNotFoundException("Customer not found with national ID: 00000000000"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptService).recordFailedAttempt("00000000000");
    }
}