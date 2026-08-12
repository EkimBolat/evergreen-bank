package com.ekim.bankingapi.account;

import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.customer.Customer;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.notification.NotificationType;
import com.ekim.bankingapi.transaction.Transaction;
import com.ekim.bankingapi.transaction.TransactionRepository;
import com.ekim.bankingapi.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterestAccrualServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

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
    void applyInterest_shouldCreditMonthlyInterest_whenRateIsPositive() {
        interestAccrualService.applyInterest(savingsAccount);

        // 12% annual on 1200 balance -> 1% monthly -> 12.00 interest
        assertThat(savingsAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1212));

        verify(accountRepository).save(savingsAccount);
        verify(transactionRepository).save(argThat((Transaction t) ->
                t.getType() == TransactionType.INTEREST
                        && t.getAmount().compareTo(BigDecimal.valueOf(12)) == 0
                        && t.getBalanceAfter().compareTo(BigDecimal.valueOf(1212)) == 0
        ));
        verify(notificationService).notify(eq(1L), eq(NotificationType.INTEREST_CREDITED), anyString(), anyString());
    }

    @Test
    void applyInterest_shouldDoNothing_whenInterestRateIsNull() {
        savingsAccount.setInterestRate(null);

        interestAccrualService.applyInterest(savingsAccount);

        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(notificationService, never()).notify(anyLong(), any(), anyString(), anyString());
    }

    @Test
    void applyInterest_shouldDoNothing_whenInterestRateIsZero() {
        savingsAccount.setInterestRate(BigDecimal.ZERO);

        interestAccrualService.applyInterest(savingsAccount);

        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void applyMonthlyInterest_shouldProcessAllSavingsAccounts() {
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

        verify(accountRepository, times(2)).save(any(Account.class));
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    void applyMonthlyInterest_shouldContinueProcessing_whenOneAccountFails() {
        Account failing = new Account();
        failing.setId(3L);
        failing.setAccountNumber("TR3333333333");
        failing.setAccountType(AccountType.SAVINGS);
        failing.setBalance(BigDecimal.valueOf(500));
        failing.setInterestRate(BigDecimal.valueOf(6));
        failing.setCustomer(null); // will cause NPE when resolving customer id

        when(accountRepository.findByAccountType(AccountType.SAVINGS))
                .thenReturn(List.of(failing, savingsAccount));

        interestAccrualService.applyMonthlyInterest();

        verify(accountRepository, times(1)).save(savingsAccount);
    }
}
