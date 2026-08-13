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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CustomerService customerService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final TotpService totpService;

    public AuthResponse register(RegisterRequest request) {
        Customer customer = customerService.findCustomerEntityByNationalId(request.getNationalId());

        if (userRepository.existsByCustomerId(customer.getId())) {
            log.warn("Registration rejected - customer already has account: nationalId={}", request.getNationalId());
            throw new DuplicateResourceException("This customer already has a login account");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration rejected - email already in use: email={}", request.getEmail());
            throw new DuplicateResourceException("Email already in use: " + request.getEmail());
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setCustomer(customer);

        User saved = userRepository.save(user);

        String token = jwtService.generateToken(saved.getEmail(), saved.getId(), customer.getId(), saved.getRole().name());
        String refreshToken = refreshTokenService.createRefreshToken(saved);

        auditLogService.log("User", saved.getId(), "REGISTER", request.getNationalId(), "New user registered: " + saved.getEmail());

        log.info("Registration successful: userId={}, customerId={}", saved.getId(), customer.getId());

        return new AuthResponse(saved.getId(), customer.getId(), saved.getEmail(), "Registration successful", token, saved.getRole(), refreshToken, false, null);
    }

    public AuthResponse login(LoginRequest request) {
        String nationalId = request.getNationalId();

        loginAttemptService.checkNotLocked(nationalId);

        Customer customer;
        try {
            customer = customerService.findCustomerEntityByNationalId(nationalId);
        } catch (ResourceNotFoundException ex) {
            log.warn("Login failed - national ID not found: nationalId={}", nationalId);
            loginAttemptService.recordFailedAttempt(nationalId);
            auditLogService.log("User", null, "LOGIN_FAILED", nationalId, "National ID not found");
            throw new InvalidCredentialsException("Invalid national ID or password");
        }

        User user = userRepository.findByCustomerId(customer.getId())
                .orElseThrow(() -> {
                    log.warn("Login failed - no account for national ID: nationalId={}", nationalId);
                    if (loginAttemptService.recordFailedAttempt(nationalId)) {
                        notifyAccountLocked(customer);
                    }
                    auditLogService.log("User", null, "LOGIN_FAILED", nationalId, "No account for national ID");
                    return new InvalidCredentialsException("Invalid national ID or password");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed - wrong password: nationalId={}", nationalId);
            if (loginAttemptService.recordFailedAttempt(nationalId)) {
                notifyAccountLocked(customer);
            }
            auditLogService.log("User", user.getId(), "LOGIN_FAILED", nationalId, "Wrong password");
            throw new InvalidCredentialsException("Invalid national ID or password");
        }

        loginAttemptService.recordSuccessfulLogin(nationalId);

        if (user.isTwoFactorEnabled()) {
            String pendingToken = jwtService.generateTwoFactorPendingToken(user.getEmail());
            auditLogService.log("User", user.getId(), "LOGIN_2FA_PENDING", nationalId, "Password verified, awaiting two-factor code");
            log.info("Login pending two-factor code: userId={}, nationalId={}", user.getId(), nationalId);
            return AuthResponse.twoFactorRequired(pendingToken);
        }

        String token = jwtService.generateToken(user.getEmail(), user.getId(), customer.getId(), user.getRole().name());
        String refreshToken = refreshTokenService.createRefreshToken(user);

        auditLogService.log("User", user.getId(), "LOGIN_SUCCESS", nationalId, "User logged in: " + user.getEmail());

        log.info("Login successful: userId={}, nationalId={}, role={}", user.getId(), nationalId, user.getRole());

        return new AuthResponse(user.getId(), customer.getId(), user.getEmail(), "Login successful", token, user.getRole(), refreshToken, false, null);
    }

    public AuthResponse verifyTwoFactor(TwoFactorVerifyRequest request) {
        if (!jwtService.isTwoFactorPendingToken(request.getPendingToken())) {
            throw new InvalidCredentialsException("Invalid or expired two-factor session");
        }

        String email = jwtService.extractEmail(request.getPendingToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired two-factor session"));

        if (!totpService.verifyCode(user.getTwoFactorSecret(), request.getCode())) {
            log.warn("Two-factor verification failed: userId={}", user.getId());
            auditLogService.log("User", user.getId(), "LOGIN_2FA_FAILED", user.getEmail(), "Invalid two-factor code");
            throw new InvalidCredentialsException("Invalid two-factor code");
        }

        Customer customer = user.getCustomer();
        String token = jwtService.generateToken(user.getEmail(), user.getId(), customer.getId(), user.getRole().name());
        String refreshToken = refreshTokenService.createRefreshToken(user);

        auditLogService.log("User", user.getId(), "LOGIN_SUCCESS", user.getEmail(), "Two-factor verified, login completed: " + user.getEmail());

        log.info("Two-factor login successful: userId={}", user.getId());

        return new AuthResponse(user.getId(), customer.getId(), user.getEmail(), "Login successful", token, user.getRole(), refreshToken, false, null);
    }

    public TwoFactorSetupResponse setupTwoFactor() {
        User user = currentUser();

        String secret = totpService.generateSecret();
        user.setTwoFactorSecret(secret);
        user.setTwoFactorEnabled(false);
        userRepository.save(user);

        auditLogService.log("User", user.getId(), "2FA_SETUP_STARTED", user.getEmail(), "Two-factor secret generated, awaiting confirmation");

        return new TwoFactorSetupResponse(secret, totpService.getOtpAuthUri(user.getEmail(), secret));
    }

    public void enableTwoFactor(TwoFactorCodeRequest request) {
        User user = currentUser();

        if (user.getTwoFactorSecret() == null) {
            throw new InvalidRequestException("Call /2fa/setup before enabling two-factor authentication");
        }
        if (!totpService.verifyCode(user.getTwoFactorSecret(), request.getCode())) {
            throw new InvalidCredentialsException("Invalid two-factor code");
        }

        user.setTwoFactorEnabled(true);
        userRepository.save(user);

        auditLogService.log("User", user.getId(), "2FA_ENABLED", user.getEmail(), "Two-factor authentication enabled");
        notificationService.notify(user.getCustomer().getId(), NotificationType.TWO_FACTOR_ENABLED,
                "Two-Factor Authentication Enabled", "Two-factor authentication was enabled on your account.");

        log.info("Two-factor enabled: userId={}", user.getId());
    }

    public void disableTwoFactor(TwoFactorCodeRequest request) {
        User user = currentUser();

        if (!user.isTwoFactorEnabled() || !totpService.verifyCode(user.getTwoFactorSecret(), request.getCode())) {
            throw new InvalidCredentialsException("Invalid two-factor code");
        }

        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        userRepository.save(user);

        auditLogService.log("User", user.getId(), "2FA_DISABLED", user.getEmail(), "Two-factor authentication disabled");
        notificationService.notify(user.getCustomer().getId(), NotificationType.TWO_FACTOR_DISABLED,
                "Two-Factor Authentication Disabled", "Two-factor authentication was disabled on your account.");

        log.info("Two-factor disabled: userId={}", user.getId());
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    public AuthResponse refresh(String refreshToken) {
        User user = refreshTokenService.validateAndGetUser(refreshToken);

        String newAccessToken = jwtService.generateToken(user.getEmail(), user.getId(), user.getCustomer().getId(), user.getRole().name());
        String newRefreshToken = refreshTokenService.createRefreshToken(user);

        log.info("Token refreshed: userId={}", user.getId());

        return new AuthResponse(user.getId(), user.getCustomer().getId(), user.getEmail(), "Token refreshed", newAccessToken, user.getRole(), newRefreshToken, false, null);
    }

    private void notifyAccountLocked(Customer customer) {
        notificationService.notify(customer.getId(), NotificationType.ACCOUNT_LOCKED,
                "Account Locked", "Your account was locked due to too many failed login attempts. Please try again in 15 minutes.");
    }
}