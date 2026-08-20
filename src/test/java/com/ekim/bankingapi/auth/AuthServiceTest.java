package com.ekim.bankingapi.auth;

import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.customer.Customer;
import com.ekim.bankingapi.customer.CustomerService;
import com.ekim.bankingapi.exception.DuplicateResourceException;
import com.ekim.bankingapi.exception.InvalidCredentialsException;
import com.ekim.bankingapi.exception.InvalidRequestException;
import com.ekim.bankingapi.exception.ResourceNotFoundException;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.notification.NotificationType;
import com.ekim.bankingapi.security.JwtService;
import com.ekim.bankingapi.security.LoginAttemptService;
import com.ekim.bankingapi.security.RefreshTokenService;
import com.ekim.bankingapi.security.TotpService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
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

    @Mock
    private TotpService totpService;

    @InjectMocks
    private AuthService authService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId(1L);
        customer.setNationalId("12345678901");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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

    @Test
    void login_shouldReturnPendingTwoFactor_whenTwoFactorEnabled() {
        LoginRequest request = new LoginRequest();
        request.setNationalId("12345678901");
        request.setPassword("plain-password");

        User user = new User();
        user.setId(1L);
        user.setEmail("ahmet@example.com");
        user.setPassword("hashed-password");
        user.setCustomer(customer);
        user.setTwoFactorEnabled(true);

        when(customerService.findCustomerEntityByNationalId("12345678901")).thenReturn(customer);
        when(userRepository.findByCustomerId(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", "hashed-password")).thenReturn(true);
        when(jwtService.generateTwoFactorPendingToken("ahmet@example.com")).thenReturn("pending-token");

        AuthResponse response = authService.login(request);

        assertThat(response.isTwoFactorRequired()).isTrue();
        assertThat(response.getPendingToken()).isEqualTo("pending-token");
        assertThat(response.getToken()).isNull();
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void verifyTwoFactor_shouldReturnFullTokens_whenCodeIsValid() {
        User user = new User();
        user.setId(1L);
        user.setEmail("ahmet@example.com");
        user.setCustomer(customer);
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret("SECRET");

        TwoFactorVerifyRequest request = new TwoFactorVerifyRequest();
        request.setPendingToken("pending-token");
        request.setCode("123456");

        when(jwtService.isTwoFactorPendingToken("pending-token")).thenReturn(true);
        when(jwtService.extractEmail("pending-token")).thenReturn("ahmet@example.com");
        when(userRepository.findByEmail("ahmet@example.com")).thenReturn(Optional.of(user));
        when(totpService.verifyCode("SECRET", "123456")).thenReturn(true);
        when(jwtService.generateToken(anyString(), anyLong(), anyLong(), anyString())).thenReturn("fake-jwt-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("fake-refresh-token");

        AuthResponse response = authService.verifyTwoFactor(request);

        assertThat(response.isTwoFactorRequired()).isFalse();
        assertThat(response.getToken()).isEqualTo("fake-jwt-token");
        assertThat(response.getRefreshToken()).isEqualTo("fake-refresh-token");
        verify(loginAttemptService).recordSuccessfulLogin("12345678901");
    }

    @Test
    void verifyTwoFactor_shouldThrow_whenCodeIsInvalid() {
        User user = new User();
        user.setId(1L);
        user.setEmail("ahmet@example.com");
        user.setCustomer(customer);
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret("SECRET");

        TwoFactorVerifyRequest request = new TwoFactorVerifyRequest();
        request.setPendingToken("pending-token");
        request.setCode("000000");

        when(jwtService.isTwoFactorPendingToken("pending-token")).thenReturn(true);
        when(jwtService.extractEmail("pending-token")).thenReturn("ahmet@example.com");
        when(userRepository.findByEmail("ahmet@example.com")).thenReturn(Optional.of(user));
        when(totpService.verifyCode("SECRET", "000000")).thenReturn(false);

        assertThatThrownBy(() -> authService.verifyTwoFactor(request))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptService).recordFailedAttempt("12345678901");
    }

    @Test
    void verifyTwoFactor_shouldLockAccount_afterRepeatedFailedCodes() {
        User user = new User();
        user.setId(1L);
        user.setEmail("ahmet@example.com");
        user.setCustomer(customer);
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret("SECRET");

        TwoFactorVerifyRequest request = new TwoFactorVerifyRequest();
        request.setPendingToken("pending-token");
        request.setCode("000000");

        when(jwtService.isTwoFactorPendingToken("pending-token")).thenReturn(true);
        when(jwtService.extractEmail("pending-token")).thenReturn("ahmet@example.com");
        when(userRepository.findByEmail("ahmet@example.com")).thenReturn(Optional.of(user));
        when(totpService.verifyCode("SECRET", "000000")).thenReturn(false);
        when(loginAttemptService.recordFailedAttempt("12345678901")).thenReturn(true);

        assertThatThrownBy(() -> authService.verifyTwoFactor(request))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(notificationService).notify(eq(1L), eq(NotificationType.ACCOUNT_LOCKED), anyString(), anyString());
    }

    @Test
    void verifyTwoFactor_shouldThrow_whenPendingTokenInvalid() {
        TwoFactorVerifyRequest request = new TwoFactorVerifyRequest();
        request.setPendingToken("bad-token");
        request.setCode("123456");

        when(jwtService.isTwoFactorPendingToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.verifyTwoFactor(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void getTwoFactorStatus_shouldReflectCurrentUserState() {
        User user = new User();
        user.setId(1L);
        user.setEmail("ahmet@example.com");
        user.setCustomer(customer);
        user.setTwoFactorEnabled(true);
        authenticateAs("ahmet@example.com");

        when(userRepository.findByEmail("ahmet@example.com")).thenReturn(Optional.of(user));

        TwoFactorStatusResponse response = authService.getTwoFactorStatus();

        assertThat(response.isEnabled()).isTrue();
    }

    @Test
    void setupTwoFactor_shouldGenerateAndPersistSecret_forCurrentUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("ahmet@example.com");
        user.setCustomer(customer);
        authenticateAs("ahmet@example.com");

        when(userRepository.findByEmail("ahmet@example.com")).thenReturn(Optional.of(user));
        when(totpService.generateSecret()).thenReturn("NEWSECRET");
        when(totpService.getOtpAuthUri("ahmet@example.com", "NEWSECRET")).thenReturn("otpauth://totp/EvergreenBank:ahmet@example.com");

        TwoFactorSetupResponse response = authService.setupTwoFactor();

        assertThat(response.getSecret()).isEqualTo("NEWSECRET");
        assertThat(user.getTwoFactorSecret()).isEqualTo("NEWSECRET");
        assertThat(user.isTwoFactorEnabled()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void setupTwoFactor_shouldThrow_whenAlreadyEnabled() {
        User user = new User();
        user.setId(1L);
        user.setEmail("ahmet@example.com");
        user.setCustomer(customer);
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret("EXISTING-SECRET");
        authenticateAs("ahmet@example.com");

        when(userRepository.findByEmail("ahmet@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.setupTwoFactor())
                .isInstanceOf(InvalidRequestException.class);

        assertThat(user.getTwoFactorSecret()).isEqualTo("EXISTING-SECRET");
        verify(userRepository, never()).save(any());
    }

    @Test
    void enableTwoFactor_shouldEnable_whenCodeIsValid() {
        User user = new User();
        user.setId(1L);
        user.setEmail("ahmet@example.com");
        user.setCustomer(customer);
        user.setTwoFactorSecret("SECRET");
        authenticateAs("ahmet@example.com");

        TwoFactorCodeRequest request = new TwoFactorCodeRequest();
        request.setCode("123456");

        when(userRepository.findByEmail("ahmet@example.com")).thenReturn(Optional.of(user));
        when(totpService.verifyCode("SECRET", "123456")).thenReturn(true);

        authService.enableTwoFactor(request);

        assertThat(user.isTwoFactorEnabled()).isTrue();
        verify(notificationService).notify(eq(1L), eq(NotificationType.TWO_FACTOR_ENABLED), anyString(), anyString());
    }

    @Test
    void enableTwoFactor_shouldThrow_whenCodeIsInvalid() {
        User user = new User();
        user.setId(1L);
        user.setEmail("ahmet@example.com");
        user.setCustomer(customer);
        user.setTwoFactorSecret("SECRET");
        authenticateAs("ahmet@example.com");

        TwoFactorCodeRequest request = new TwoFactorCodeRequest();
        request.setCode("000000");

        when(userRepository.findByEmail("ahmet@example.com")).thenReturn(Optional.of(user));
        when(totpService.verifyCode("SECRET", "000000")).thenReturn(false);

        assertThatThrownBy(() -> authService.enableTwoFactor(request))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(user.isTwoFactorEnabled()).isFalse();
    }

    @Test
    void disableTwoFactor_shouldDisableAndClearSecret_whenCodeIsValid() {
        User user = new User();
        user.setId(1L);
        user.setEmail("ahmet@example.com");
        user.setCustomer(customer);
        user.setTwoFactorEnabled(true);
        user.setTwoFactorSecret("SECRET");
        authenticateAs("ahmet@example.com");

        TwoFactorCodeRequest request = new TwoFactorCodeRequest();
        request.setCode("123456");

        when(userRepository.findByEmail("ahmet@example.com")).thenReturn(Optional.of(user));
        when(totpService.verifyCode("SECRET", "123456")).thenReturn(true);

        authService.disableTwoFactor(request);

        assertThat(user.isTwoFactorEnabled()).isFalse();
        assertThat(user.getTwoFactorSecret()).isNull();
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }
}