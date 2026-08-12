package com.ekim.bankingapi.account;

import com.ekim.bankingapi.branch.BranchService;
import com.ekim.bankingapi.customer.Customer;
import com.ekim.bankingapi.customer.CustomerService;
import com.ekim.bankingapi.exception.DuplicateResourceException;
import com.ekim.bankingapi.exception.InvalidRequestException;
import com.ekim.bankingapi.exception.ResourceNotFoundException;
import com.ekim.bankingapi.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private BranchService branchService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AccountService accountService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId(1L);
        customer.setFirstName("Ahmet");
        customer.setLastName("Yılmaz");
    }

    @Test
    void createAccount_shouldSucceed_whenCheckingAccountWithNoInterestRate() {
        AccountRequest request = new AccountRequest();
        request.setAccountType(AccountType.CHECKING);

        when(customerService.findCustomerEntityById(1L)).thenReturn(customer);
        when(accountRepository.existsByCustomerId(1L)).thenReturn(false);
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account acc = invocation.getArgument(0);
            acc.setId(1L);
            return acc;
        });

        AccountResponse response = accountService.createAccount(1L, request);

        assertThat(response.getAccountType()).isEqualTo(AccountType.CHECKING);
        assertThat(response.getInterestRate()).isNull();
        assertThat(response.getBranchId()).isNull();
    }

    @Test
    void createAccount_shouldSucceed_whenSavingsAccountWithValidInterestRate() {
        AccountRequest request = new AccountRequest();
        request.setAccountType(AccountType.SAVINGS);
        request.setInterestRate(BigDecimal.valueOf(2.5));

        when(customerService.findCustomerEntityById(1L)).thenReturn(customer);
        when(accountRepository.existsByCustomerId(1L)).thenReturn(false);
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account acc = invocation.getArgument(0);
            acc.setId(1L);
            return acc;
        });

        AccountResponse response = accountService.createAccount(1L, request);

        assertThat(response.getAccountType()).isEqualTo(AccountType.SAVINGS);
        assertThat(response.getInterestRate()).isEqualByComparingTo(BigDecimal.valueOf(2.5));
    }

    @Test
    void createAccount_shouldThrow_whenSavingsAccountHasNoInterestRate() {
        AccountRequest request = new AccountRequest();
        request.setAccountType(AccountType.SAVINGS);

        when(customerService.findCustomerEntityById(1L)).thenReturn(customer);
        when(accountRepository.existsByCustomerId(1L)).thenReturn(false);

        assertThatThrownBy(() -> accountService.createAccount(1L, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("require a positive interest rate");

        verify(accountRepository, never()).save(any());
    }

    @Test
    void createAccount_shouldThrow_whenCheckingAccountHasInterestRate() {
        AccountRequest request = new AccountRequest();
        request.setAccountType(AccountType.CHECKING);
        request.setInterestRate(BigDecimal.valueOf(1.0));

        when(customerService.findCustomerEntityById(1L)).thenReturn(customer);
        when(accountRepository.existsByCustomerId(1L)).thenReturn(false);

        assertThatThrownBy(() -> accountService.createAccount(1L, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("cannot have an interest rate");
    }

    @Test
    void createAccount_shouldThrow_whenCustomerAlreadyHasAccount() {
        AccountRequest request = new AccountRequest();
        request.setAccountType(AccountType.CHECKING);

        when(customerService.findCustomerEntityById(1L)).thenReturn(customer);
        when(accountRepository.existsByCustomerId(1L)).thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount(1L, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already has an account");

        verify(accountRepository, never()).save(any());
    }

    @Test
    void getAccountById_shouldThrow_whenNotFound() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccountById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }
}