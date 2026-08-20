package com.ekim.bankingapi.transfer;

import com.ekim.bankingapi.account.Account;
import com.ekim.bankingapi.account.AccountService;
import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.customer.Customer;
import com.ekim.bankingapi.exception.InsufficientBalanceException;
import com.ekim.bankingapi.exception.InvalidRequestException;
import com.ekim.bankingapi.nature.NatureService;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.transaction.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private NatureService natureService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private TransferService transferService;

    private Account fromAccount;
    private Account toAccount;

    @BeforeEach
    void setUp() {
        Customer fromCustomer = new Customer();
        fromCustomer.setId(1L);

        Customer toCustomer = new Customer();
        toCustomer.setId(2L);

        fromAccount = new Account();
        fromAccount.setId(1L);
        fromAccount.setAccountNumber("TR1111111111");
        fromAccount.setBalance(BigDecimal.valueOf(500));
        fromAccount.setCustomer(fromCustomer);

        toAccount = new Account();
        toAccount.setId(2L);
        toAccount.setAccountNumber("TR2222222222");
        toAccount.setBalance(BigDecimal.valueOf(100));
        toAccount.setCustomer(toCustomer);
    }

    @Test
    void transfer_shouldMoveMoneyBetweenAccounts_whenValid() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(1L);
        request.setToAccountId(2L);
        request.setAmount(BigDecimal.valueOf(150));

        when(accountService.requireOwnedAccount(1L)).thenReturn(fromAccount);
        when(accountService.findAccountEntityById(2L)).thenReturn(toAccount);
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(1L);
            return t;
        });

        TransferResponse response = transferService.transfer(request);

        assertThat(fromAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(350));
        assertThat(toAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(250));
        assertThat(response.getFromAccountNumber()).isEqualTo("TR1111111111");
        assertThat(response.getToAccountNumber()).isEqualTo("TR2222222222");

        verify(transactionRepository, times(2)).save(any());
    }

    @Test
    void transfer_shouldThrow_whenSameAccount() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(1L);
        request.setToAccountId(1L);
        request.setAmount(BigDecimal.valueOf(50));

        assertThatThrownBy(() -> transferService.transfer(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("same account");
    }

    @Test
    void transfer_shouldThrow_whenInsufficientBalance() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(1L);
        request.setToAccountId(2L);
        request.setAmount(BigDecimal.valueOf(999999));

        when(accountService.requireOwnedAccount(1L)).thenReturn(fromAccount);
        when(accountService.findAccountEntityById(2L)).thenReturn(toAccount);

        assertThatThrownBy(() -> transferService.transfer(request))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(transferRepository, never()).save(any());
    }
}