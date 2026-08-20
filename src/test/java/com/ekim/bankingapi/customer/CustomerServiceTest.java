package com.ekim.bankingapi.customer;

import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.branch.BranchService;
import com.ekim.bankingapi.exception.DuplicateResourceException;
import com.ekim.bankingapi.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private BranchService branchService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private CustomerService customerService;

    private CustomerRequest request;

    @BeforeEach
    void setUp() {
        request = new CustomerRequest();
        request.setFirstName("Ahmet");
        request.setLastName("Yılmaz");
        request.setEmail("ahmet@example.com");
        request.setPhoneNumber("05551234567");
        request.setNationalId("12345678901");
        request.setAge(25);
        request.setAddress("İstanbul");
    }

    @Test
    void createCustomer_shouldSucceed_whenEmailAndNationalIdAreUnique() {
        when(customerRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(customerRepository.existsByNationalId(request.getNationalId())).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer c = invocation.getArgument(0);
            c.setId(1L);
            return c;
        });

        CustomerResponse response = customerService.createCustomer(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("ahmet@example.com");
        assertThat(response.getBranchId()).isNull();
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void createCustomer_shouldThrow_whenEmailAlreadyExists() {
        when(customerRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email already in use");

        verify(customerRepository, never()).save(any());
    }

    @Test
    void createCustomer_shouldThrow_whenNationalIdAlreadyExists() {
        when(customerRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(customerRepository.existsByNationalId(request.getNationalId())).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("National ID already registered");
    }

    @Test
    void getCustomerById_shouldReturnCustomer_whenExists() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFirstName("Ahmet");
        customer.setEmail("ahmet@example.com");

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getCustomerById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getFirstName()).isEqualTo("Ahmet");
    }

    @Test
    void getCustomerById_shouldThrow_whenNotFound() {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomerById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void getCustomerById_shouldReturnDailyPoints_whenEarnedToday() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setDailyNaturePoints(35);
        customer.setLastPointsDate(LocalDate.now());

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getCustomerById(1L);

        assertThat(response.getDailyNaturePoints()).isEqualTo(35);
    }

    @Test
    void getCustomerById_shouldReturnZeroDailyPoints_whenLastEarnedOnAPreviousDay() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setDailyNaturePoints(50);
        customer.setLastPointsDate(LocalDate.now().minusDays(1));

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getCustomerById(1L);

        assertThat(response.getDailyNaturePoints()).isZero();
    }

    @Test
    void getMyProfile_shouldReturnAuthenticatedCustomersProfile() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFirstName("Ahmet");
        authenticateAs(1L);

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getMyProfile();

        assertThat(response.getFirstName()).isEqualTo("Ahmet");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long customerId) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken("ahmet@example.com", null, List.of());
        authToken.setDetails(customerId);
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}