package com.ekim.bankingapi.auth;

import com.ekim.bankingapi.customer.CustomerRequest;
import com.ekim.bankingapi.customer.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a single default ADMIN account on a fresh database, since there is no other
 * way to create the very first customer/user: customer creation requires an
 * authenticated caller, and registration requires an existing customer.
 * No-ops once any ADMIN user exists, so it is safe to run on every startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CustomerService customerService;
    private final AuthService authService;

    @Value("${admin.bootstrap.enabled:true}")
    private boolean enabled;

    @Value("${admin.bootstrap.national-id:00000000000}")
    private String nationalId;

    @Value("${admin.bootstrap.email:admin@evergreenbank.local}")
    private String email;

    @Value("${admin.bootstrap.password:ChangeMe123!}")
    private String password;

    @Value("${admin.bootstrap.first-name:Admin}")
    private String firstName;

    @Value("${admin.bootstrap.last-name:User}")
    private String lastName;

    @Value("${admin.bootstrap.phone-number:05550000000}")
    private String phoneNumber;

    @Value("${admin.bootstrap.age:30}")
    private int age;

    @Override
    public void run(String... args) {
        if (!enabled || userRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        try {
            CustomerRequest customerRequest = new CustomerRequest();
            customerRequest.setFirstName(firstName);
            customerRequest.setLastName(lastName);
            customerRequest.setEmail(email);
            customerRequest.setPhoneNumber(phoneNumber);
            customerRequest.setNationalId(nationalId);
            customerRequest.setAge(age);
            customerService.createCustomer(customerRequest);

            RegisterRequest registerRequest = new RegisterRequest();
            registerRequest.setNationalId(nationalId);
            registerRequest.setEmail(email);
            registerRequest.setPassword(password);
            authService.register(registerRequest);

            User admin = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalStateException("Admin user vanished right after registration"));
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);

            log.warn("Bootstrapped default admin account: nationalId={}, email={} " +
                            "-- this is a development default; change admin.bootstrap.* before deploying anywhere real",
                    nationalId, email);
        } catch (Exception e) {
            log.error("Failed to bootstrap default admin account: {}", e.getMessage(), e);
        }
    }
}
