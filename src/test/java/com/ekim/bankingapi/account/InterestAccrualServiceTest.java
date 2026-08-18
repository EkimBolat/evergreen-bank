package com.ekim.bankingapi.account;

import com.ekim.bankingapi.customer.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterestAccrualServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private InterestPostingService interestPostingService;

    @InjectMocks
    private InterestAccrualService interestAccrualService;

    private Account savingsAccount;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer();
        customer.setId(1L);

        savingsAccount = new Account();
        savingsAccount.setId(1L);
        savingsAccount.setAccountNumber("TR1111111111");
        savingsAccount.setAccountType(AccountType.SAVINGS);
        savingsAccount.setBalance(BigDecimal.valueOf(1200));
        savingsAccount.setInterestRate(BigDecimal.valueOf(12));
        savingsAccount.setCustomer(customer);
    }

    @Test
    void applyMonthlyInterest_shouldDelegateToPostingService_forEachSavingsAccount() {
        Account another = new Account();
        another.setId(2L);
        another.setAccountNumber("TR2222222222");
        another.setAccountType(AccountType.SAVINGS);
        another.setBalance(BigDecimal.valueOf(500));
        another.setInterestRate(BigDecimal.valueOf(6));
        Customer otherCustomer = new Customer();
        otherCustomer.setId(2L);
        another.setCustomer(otherCustomer);

        when(accountRepository.findByAccountType(AccountType.SAVINGS))
                .thenReturn(List.of(savingsAccount, another));

        interestAccrualService.applyMonthlyInterest();

        verify(interestPostingService).applyInterest(savingsAccount);
        verify(interestPostingService).applyInterest(another);
    }

    @Test
    void applyMonthlyInterest_shouldContinueProcessing_whenOneAccountFails() {
        Account failing = new Account();
        failing.setId(3L);
        failing.setAccountNumber("TR3333333333");
        failing.setAccountType(AccountType.SAVINGS);

        when(accountRepository.findByAccountType(AccountType.SAVINGS))
                .thenReturn(List.of(failing, savingsAccount));
        doThrow(new RuntimeException("boom")).when(interestPostingService).applyInterest(failing);

        interestAccrualService.applyMonthlyInterest();

        verify(interestPostingService).applyInterest(failing);
        verify(interestPostingService).applyInterest(savingsAccount);
    }
}
