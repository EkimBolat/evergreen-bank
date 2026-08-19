package com.ekim.bankingapi.auth;

import com.ekim.bankingapi.customer.CustomerRequest;
import com.ekim.bankingapi.customer.CustomerService;
import com.ekim.bankingapi.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AdminBootstrapRunner adminBootstrapRunner;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adminBootstrapRunner, "enabled", true);
        ReflectionTestUtils.setField(adminBootstrapRunner, "nationalId", "00000000000");
        ReflectionTestUtils.setField(adminBootstrapRunner, "email", "admin@evergreenbank.local");
        ReflectionTestUtils.setField(adminBootstrapRunner, "password", "ChangeMe123!");
        ReflectionTestUtils.setField(adminBootstrapRunner, "firstName", "Admin");
        ReflectionTestUtils.setField(adminBootstrapRunner, "lastName", "User");
        ReflectionTestUtils.setField(adminBootstrapRunner, "phoneNumber", "05550000000");
        ReflectionTestUtils.setField(adminBootstrapRunner, "age", 30);
    }

    @Test
    void run_shouldCreateAdmin_whenNoAdminExists() throws Exception {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        User createdUser = new User();
        createdUser.setId(1L);
        createdUser.setEmail("admin@evergreenbank.local");
        createdUser.setRole(Role.CUSTOMER);
        when(userRepository.findByEmail("admin@evergreenbank.local")).thenReturn(Optional.of(createdUser));

        adminBootstrapRunner.run();

        verify(customerService).createCustomer(any(CustomerRequest.class));
        verify(authService).register(any(RegisterRequest.class));
        verify(userRepository).save(argThat(u -> u.getRole() == Role.ADMIN));
    }

    @Test
    void run_shouldSkip_whenAdminAlreadyExists() throws Exception {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        adminBootstrapRunner.run();

        verify(customerService, never()).createCustomer(any());
        verify(authService, never()).register(any());
    }

    @Test
    void run_shouldSkip_whenDisabled() throws Exception {
        ReflectionTestUtils.setField(adminBootstrapRunner, "enabled", false);

        adminBootstrapRunner.run();

        verifyNoInteractions(customerService, authService);
        verify(userRepository, never()).existsByRole(any());
    }

    @Test
    void run_shouldNotPropagate_whenBootstrapFails() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        doThrow(new DuplicateResourceException("already exists")).when(customerService).createCustomer(any());

        assertThatCode(() -> adminBootstrapRunner.run()).doesNotThrowAnyException();

        verify(authService, never()).register(any());
    }
}
