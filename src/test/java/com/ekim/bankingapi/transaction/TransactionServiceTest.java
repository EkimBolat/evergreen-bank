package com.ekim.bankingapi.transaction;

import com.ekim.bankingapi.account.Account;
import com.ekim.bankingapi.account.AccountService;
import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.customer.Customer;
import com.ekim.bankingapi.exception.InsufficientBalanceException;
import com.ekim.bankingapi.exception.InvalidRequestException;
import com.ekim.bankingapi.exception.ResourceNotFoundException;
import com.ekim.bankingapi.nature.NatureService;
import com.ekim.bankingapi.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private NatureService natureService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TransactionService transactionService;

    private Account account;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer();
        customer.setId(1L);

        account = new Account();
        account.setId(1L);
        account.setAccountNumber("TR1234567890");
        account.setBalance(BigDecimal.valueOf(500));
        account.setCustomer(customer);
    }

    @Test
    void deposit_shouldIncreaseBalance_whenAmountIsValid() {
        when(accountService.findAccountEntityById(1L)).thenReturn(account);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.deposit(1L, BigDecimal.valueOf(100));

        assertThat(response.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.getBalanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(600));
    }

    @Test
    void deposit_shouldThrow_whenAmountIsZeroOrNegative() {
        assertThatThrownBy(() -> transactionService.deposit(1L, BigDecimal.ZERO))
                .isInstanceOf(InvalidRequestException.class);

        assertThatThrownBy(() -> transactionService.deposit(1L, BigDecimal.valueOf(-50)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void withdraw_shouldDecreaseBalance_whenSufficientFunds() {
        when(accountService.findAccountEntityById(1L)).thenReturn(account);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.withdraw(1L, BigDecimal.valueOf(200));

        assertThat(response.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(response.getBalanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(300));
    }

    @Test
    void withdraw_shouldThrow_whenInsufficientBalance() {
        when(accountService.findAccountEntityById(1L)).thenReturn(account);

        assertThatThrownBy(() -> transactionService.withdraw(1L, BigDecimal.valueOf(999999)))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void exportStatementCsv_shouldContainHeaderAndEachTransaction() {
        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setAmount(BigDecimal.valueOf(100));
        transaction.setBalanceAfter(BigDecimal.valueOf(600));
        transaction.setTimestamp(LocalDateTime.of(2026, 1, 15, 10, 30));

        when(accountService.findAccountEntityById(1L)).thenReturn(account);
        when(transactionRepository.findByAccountIdOrderByTimestampDesc(1L)).thenReturn(List.of(transaction));

        byte[] csv = transactionService.exportStatementCsv(1L);
        String content = new String(csv, StandardCharsets.UTF_8);

        assertThat(content).startsWith("Date,Type,Amount,Balance After,Transfer Id\n");
        assertThat(content).contains("2026-01-15T10:30,DEPOSIT,100,600,\n");
    }

    @Test
    void exportStatementCsv_shouldThrow_whenAccountNotFound() {
        when(accountService.findAccountEntityById(999L))
                .thenThrow(new ResourceNotFoundException("Account not found with id: 999"));

        assertThatThrownBy(() -> transactionService.exportStatementCsv(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}