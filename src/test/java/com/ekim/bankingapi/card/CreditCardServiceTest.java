package com.ekim.bankingapi.card;

import com.ekim.bankingapi.account.Account;
import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.customer.Customer;
import com.ekim.bankingapi.exception.InvalidRequestException;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.notification.NotificationType;
import com.ekim.bankingapi.transaction.TransactionResponse;
import com.ekim.bankingapi.transaction.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditCardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CreditCardTransactionRepository creditCardTransactionRepository;

    @Mock
    private CreditCardStatementRepository creditCardStatementRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CreditCardService creditCardService;

    private Card creditCard;
    private Account account;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer();
        customer.setId(1L);

        account = new Account();
        account.setId(1L);
        account.setAccountNumber("TR1111111111");
        account.setCustomer(customer);

        creditCard = new Card();
        creditCard.setId(5L);
        creditCard.setAccount(account);
        creditCard.setCardNumber("1234567812345678");
        creditCard.setStatus(CardStatus.ACTIVE);
        creditCard.setCardType(CardType.CREDIT);
        creditCard.setCreditLimit(BigDecimal.valueOf(1000));
        creditCard.setCurrentBalance(BigDecimal.valueOf(200));
        creditCard.setApr(BigDecimal.valueOf(42));
    }

    @Test
    void charge_shouldIncreaseBalance_whenWithinAvailableCredit() {
        when(cardRepository.findById(5L)).thenReturn(Optional.of(creditCard));
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(creditCardTransactionRepository.save(any(CreditCardTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreditChargeRequest request = new CreditChargeRequest();
        request.setAmount(BigDecimal.valueOf(300));
        request.setDescription("Groceries");

        CreditCardTransactionResponse response = creditCardService.charge(5L, request);

        assertThat(response.getType()).isEqualTo(CreditCardTransactionType.PURCHASE);
        assertThat(response.getBalanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(creditCard.getCurrentBalance()).isEqualByComparingTo(BigDecimal.valueOf(500));
        verify(notificationService).notify(eq(1L), eq(NotificationType.CREDIT_CARD_CHARGED), anyString(), anyString());
    }

    @Test
    void charge_shouldThrow_whenExceedsAvailableCredit() {
        when(cardRepository.findById(5L)).thenReturn(Optional.of(creditCard));

        CreditChargeRequest request = new CreditChargeRequest();
        request.setAmount(BigDecimal.valueOf(900));

        assertThatThrownBy(() -> creditCardService.charge(5L, request))
                .isInstanceOf(InvalidRequestException.class);

        verify(cardRepository, never()).save(any());
    }

    @Test
    void charge_shouldThrow_whenCardIsNotActive() {
        creditCard.setStatus(CardStatus.BLOCKED);
        when(cardRepository.findById(5L)).thenReturn(Optional.of(creditCard));

        CreditChargeRequest request = new CreditChargeRequest();
        request.setAmount(BigDecimal.valueOf(10));

        assertThatThrownBy(() -> creditCardService.charge(5L, request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void charge_shouldThrow_whenCardIsDebit() {
        Card debitCard = new Card();
        debitCard.setId(6L);
        debitCard.setAccount(account);
        debitCard.setCardNumber("8765432187654321");
        debitCard.setStatus(CardStatus.ACTIVE);
        debitCard.setCardType(CardType.DEBIT);

        when(cardRepository.findById(6L)).thenReturn(Optional.of(debitCard));

        CreditChargeRequest request = new CreditChargeRequest();
        request.setAmount(BigDecimal.valueOf(10));

        assertThatThrownBy(() -> creditCardService.charge(6L, request))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void pay_shouldDecreaseCardBalance_andWithdrawFromLinkedAccount() {
        when(cardRepository.findById(5L)).thenReturn(Optional.of(creditCard));
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(creditCardStatementRepository.findFirstByCardIdOrderByStatementDateDesc(5L)).thenReturn(Optional.empty());
        when(creditCardTransactionRepository.save(any(CreditCardTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionService.withdraw(eq(1L), eq(BigDecimal.valueOf(150))))
                .thenReturn(mock(TransactionResponse.class));

        CreditPaymentRequest request = new CreditPaymentRequest();
        request.setAmount(BigDecimal.valueOf(150));

        CreditCardTransactionResponse response = creditCardService.pay(5L, request);

        assertThat(response.getType()).isEqualTo(CreditCardTransactionType.PAYMENT);
        assertThat(creditCard.getCurrentBalance()).isEqualByComparingTo(BigDecimal.valueOf(50));
        verify(transactionService).withdraw(1L, BigDecimal.valueOf(150));
        verify(notificationService).notify(eq(1L), eq(NotificationType.CREDIT_CARD_PAYMENT_RECEIVED), anyString(), anyString());
    }

    @Test
    void pay_shouldThrow_whenAmountExceedsCurrentBalance() {
        when(cardRepository.findById(5L)).thenReturn(Optional.of(creditCard));

        CreditPaymentRequest request = new CreditPaymentRequest();
        request.setAmount(BigDecimal.valueOf(500));

        assertThatThrownBy(() -> creditCardService.pay(5L, request))
                .isInstanceOf(InvalidRequestException.class);

        verify(transactionService, never()).withdraw(anyLong(), any());
    }

    @Test
    void pay_shouldApplyPaymentToOpenStatement() {
        CreditCardStatement statement = new CreditCardStatement();
        statement.setCard(creditCard);
        statement.setStatementBalance(BigDecimal.valueOf(200));
        statement.setPaidAmount(BigDecimal.ZERO);

        when(cardRepository.findById(5L)).thenReturn(Optional.of(creditCard));
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(creditCardStatementRepository.findFirstByCardIdOrderByStatementDateDesc(5L))
                .thenReturn(Optional.of(statement));
        when(creditCardTransactionRepository.save(any(CreditCardTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionService.withdraw(eq(1L), any())).thenReturn(mock(TransactionResponse.class));

        CreditPaymentRequest request = new CreditPaymentRequest();
        request.setAmount(BigDecimal.valueOf(120));

        creditCardService.pay(5L, request);

        assertThat(statement.getPaidAmount()).isEqualByComparingTo(BigDecimal.valueOf(120));
        verify(creditCardStatementRepository).save(statement);
    }

    @Test
    void getStatements_shouldReturnStatementsForCard() {
        CreditCardStatement statement = new CreditCardStatement();
        statement.setId(1L);
        statement.setCard(creditCard);
        statement.setStatementDate(java.time.LocalDate.of(2026, 1, 1));
        statement.setDueDate(java.time.LocalDate.of(2026, 1, 26));
        statement.setStatementBalance(BigDecimal.valueOf(200));
        statement.setMinimumPayment(BigDecimal.valueOf(50));

        when(cardRepository.findById(5L)).thenReturn(Optional.of(creditCard));
        when(creditCardStatementRepository.findByCardIdOrderByStatementDateDesc(5L)).thenReturn(List.of(statement));

        List<CreditCardStatementResponse> result = creditCardService.getStatements(5L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatementBalance()).isEqualByComparingTo(BigDecimal.valueOf(200));
    }
}
