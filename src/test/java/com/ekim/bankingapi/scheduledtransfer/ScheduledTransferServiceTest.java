package com.ekim.bankingapi.scheduledtransfer;

import com.ekim.bankingapi.account.Account;
import com.ekim.bankingapi.account.AccountService;
import com.ekim.bankingapi.customer.Customer;
import com.ekim.bankingapi.exception.InvalidCredentialsException;
import com.ekim.bankingapi.exception.ResourceNotFoundException;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.transfer.TransferService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledTransferServiceTest {

    @Mock
    private ScheduledTransferRepository scheduledTransferRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private TransferService transferService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ScheduledTransferService scheduledTransferService;

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
        fromAccount.setCustomer(fromCustomer);

        toAccount = new Account();
        toAccount.setId(2L);
        toAccount.setCustomer(toCustomer);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createScheduledTransfer_shouldSucceed_whenCallerOwnsFromAccount() {
        authenticateAs(1L);

        ScheduledTransferRequest request = new ScheduledTransferRequest();
        request.setFromAccountId(1L);
        request.setToAccountId(2L);
        request.setAmount(BigDecimal.valueOf(100));
        request.setFrequency(Frequency.MONTHLY);

        when(accountService.requireOwnedAccount(1L)).thenReturn(fromAccount);
        when(accountService.findAccountEntityById(2L)).thenReturn(toAccount);
        when(scheduledTransferRepository.save(any(ScheduledTransfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScheduledTransferResponse response = scheduledTransferService.createScheduledTransfer(request);

        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void createScheduledTransfer_shouldThrow_whenCallerDoesNotOwnFromAccount() {
        authenticateAs(2L);

        ScheduledTransferRequest request = new ScheduledTransferRequest();
        request.setFromAccountId(1L);
        request.setToAccountId(2L);
        request.setAmount(BigDecimal.valueOf(100));
        request.setFrequency(Frequency.MONTHLY);

        when(accountService.requireOwnedAccount(1L))
                .thenThrow(new InvalidCredentialsException("You do not have access to this account"));

        assertThatThrownBy(() -> scheduledTransferService.createScheduledTransfer(request))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(scheduledTransferRepository, never()).save(any());
    }

    @Test
    void getMyScheduledTransfers_shouldOnlyReturnCallersTransfers() {
        authenticateAs(1L);

        ScheduledTransfer scheduled = new ScheduledTransfer();
        scheduled.setId(9L);
        scheduled.setFromAccount(fromAccount);
        scheduled.setToAccount(toAccount);
        scheduled.setAmount(BigDecimal.valueOf(50));
        scheduled.setFrequency(Frequency.WEEKLY);
        scheduled.setActive(true);

        when(scheduledTransferRepository.findByFromAccountCustomerId(1L)).thenReturn(List.of(scheduled));

        List<ScheduledTransferResponse> result = scheduledTransferService.getMyScheduledTransfers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(9L);
    }

    @Test
    void cancelScheduledTransfer_shouldDeactivate_whenCallerOwnsIt() {
        authenticateAs(1L);

        ScheduledTransfer scheduled = new ScheduledTransfer();
        scheduled.setId(9L);
        scheduled.setFromAccount(fromAccount);
        scheduled.setToAccount(toAccount);
        scheduled.setActive(true);

        when(scheduledTransferRepository.findById(9L)).thenReturn(Optional.of(scheduled));

        scheduledTransferService.cancelScheduledTransfer(9L);

        assertThat(scheduled.isActive()).isFalse();
    }

    @Test
    void cancelScheduledTransfer_shouldThrow_whenCallerDoesNotOwnFromAccount() {
        authenticateAs(2L);

        ScheduledTransfer scheduled = new ScheduledTransfer();
        scheduled.setId(9L);
        scheduled.setFromAccount(fromAccount);
        scheduled.setToAccount(toAccount);
        scheduled.setActive(true);

        when(scheduledTransferRepository.findById(9L)).thenReturn(Optional.of(scheduled));

        assertThatThrownBy(() -> scheduledTransferService.cancelScheduledTransfer(9L))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(scheduled.isActive()).isTrue();
        verify(scheduledTransferRepository, never()).save(any());
    }

    @Test
    void cancelScheduledTransfer_shouldThrow_whenNotFound() {
        authenticateAs(1L);

        when(scheduledTransferRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduledTransferService.cancelScheduledTransfer(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void authenticateAs(Long customerId) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken("ahmet@example.com", null, List.of());
        authToken.setDetails(customerId);
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
