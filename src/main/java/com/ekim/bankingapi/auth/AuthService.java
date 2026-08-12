package com.ekim.bankingapi.auth;

import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.customer.Customer;
import com.ekim.bankingapi.customer.CustomerService;
import com.ekim.bankingapi.exception.DuplicateResourceException;
import com.ekim.bankingapi.exception.InvalidCredentialsException;
import com.ekim.bankingapi.exception.ResourceNotFoundException;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.notification.NotificationType;
import com.ekim.bankingapi.security.JwtService;
import com.ekim.bankingapi.security.LoginAttemptService;
import com.ekim.bankingapi.security.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        return new AuthResponse(saved.getId(), customer.getId(), saved.getEmail(), "Registration successful", token, saved.getRole(), refreshToken);
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

        String token = jwtService.generateToken(user.getEmail(), user.getId(), customer.getId(), user.getRole().name());
        String refreshToken = refreshTokenService.createRefreshToken(user);

        auditLogService.log("User", user.getId(), "LOGIN_SUCCESS", nationalId, "User logged in: " + user.getEmail());

        log.info("Login successful: userId={}, nationalId={}, role={}", user.getId(), nationalId, user.getRole());

        return new AuthResponse(user.getId(), customer.getId(), user.getEmail(), "Login successful", token, user.getRole(), refreshToken);
    }

    public AuthResponse refresh(String refreshToken) {
        User user = refreshTokenService.validateAndGetUser(refreshToken);

        String newAccessToken = jwtService.generateToken(user.getEmail(), user.getId(), user.getCustomer().getId(), user.getRole().name());
        String newRefreshToken = refreshTokenService.createRefreshToken(user);

        log.info("Token refreshed: userId={}", user.getId());

        return new AuthResponse(user.getId(), user.getCustomer().getId(), user.getEmail(), "Token refreshed", newAccessToken, user.getRole(), newRefreshToken);
    }

    private void notifyAccountLocked(Customer customer) {
        notificationService.notify(customer.getId(), NotificationType.ACCOUNT_LOCKED,
                "Account Locked", "Your account was locked due to too many failed login attempts. Please try again in 15 minutes.");
    }
}