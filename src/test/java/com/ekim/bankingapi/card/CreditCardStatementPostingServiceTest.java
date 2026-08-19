package com.ekim.bankingapi.card;

import com.ekim.bankingapi.account.Account;
import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.customer.Customer;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.notification.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditCardStatementPostingServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CreditCardStatementRepository statementRepository;

    @Mock
    private CreditCardTransactionRepository transactionRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CreditCardStatementPostingService postingService;

    private Card card;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer();
        customer.setId(1L);

        Account account = new Account();
        account.setId(1L);
        account.setCustomer(customer);

        card = new Card();
        card.setId(5L);
        card.setAccount(account);
        card.setCardNumber("1234567812345678");
        card.setCardType(CardType.CREDIT);
        card.setStatus(CardStatus.ACTIVE);
        card.setCreditLimit(BigDecimal.valueOf(1000));
        card.setCurrentBalance(BigDecimal.valueOf(500));
        card.setApr(BigDecimal.valueOf(24));
    }

    @Test
    void generateStatement_shouldUseCurrentBalance_andSetMinimumPaymentFloor() {
        when(statementRepository.save(any(CreditCardStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate today = LocalDate.of(2026, 3, 1);
        postingService.generateStatement(card, today);

        verify(statementRepository).save(argThat(statement ->
                statement.getStatementBalance().compareTo(BigDecimal.valueOf(500)) == 0
                        && statement.getMinimumPayment().compareTo(BigDecimal.valueOf(50)) == 0 // 3% of 500 = 15, floored to 50
                        && statement.getDueDate().equals(today.plusDays(25))
        ));
        assertThat(card.getNextStatementDate()).isEqualTo(today.plusMonths(1));
        verify(notificationService).notify(eq(1L), eq(NotificationType.CREDIT_STATEMENT_GENERATED), anyString(), anyString());
    }

    @Test
    void generateStatement_shouldSetZeroMinimumPayment_whenBalanceIsZero() {
        card.setCurrentBalance(BigDecimal.ZERO);
        when(statementRepository.save(any(CreditCardStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        postingService.generateStatement(card, LocalDate.of(2026, 3, 1));

        verify(statementRepository).save(argThat(statement -> statement.getMinimumPayment().compareTo(BigDecimal.ZERO) == 0));
    }

    @Test
    void chargeInterestIfOverdue_shouldChargeInterest_whenPastDueAndUnpaid() {
        CreditCardStatement statement = new CreditCardStatement();
        statement.setCard(card);
        statement.setStatementBalance(BigDecimal.valueOf(500));
        statement.setPaidAmount(BigDecimal.ZERO);
        statement.setDueDate(LocalDate.of(2026, 1, 1));
        statement.setInterestCharged(BigDecimal.ZERO);

        when(statementRepository.findFirstByCardIdOrderByStatementDateDesc(5L)).thenReturn(Optional.of(statement));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statementRepository.save(any(CreditCardStatement.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(CreditCardTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        postingService.chargeInterestIfOverdue(card, LocalDate.of(2026, 2, 1));

        // 24% APR / 12 = 2% monthly on 500 = 10.00
        assertThat(card.getCurrentBalance()).isEqualByComparingTo(BigDecimal.valueOf(510));
        assertThat(statement.getInterestCharged()).isEqualByComparingTo(BigDecimal.valueOf(10));
        verify(notificationService).notify(eq(1L), eq(NotificationType.CREDIT_CARD_INTEREST_CHARGED), anyString(), anyString());
    }

    @Test
    void chargeInterestIfOverdue_shouldNotCharge_whenNotYetPastDue() {
        CreditCardStatement statement = new CreditCardStatement();
        statement.setCard(card);
        statement.setStatementBalance(BigDecimal.valueOf(500));
        statement.setPaidAmount(BigDecimal.ZERO);
        statement.setDueDate(LocalDate.of(2026, 3, 1));
        statement.setInterestCharged(BigDecimal.ZERO);

        when(statementRepository.findFirstByCardIdOrderByStatementDateDesc(5L)).thenReturn(Optional.of(statement));

        postingService.chargeInterestIfOverdue(card, LocalDate.of(2026, 2, 1));

        verify(cardRepository, never()).save(any());
        verify(statementRepository, never()).save(any());
    }

    @Test
    void chargeInterestIfOverdue_shouldNotCharge_whenAlreadyPaidInFull() {
        CreditCardStatement statement = new CreditCardStatement();
        statement.setCard(card);
        statement.setStatementBalance(BigDecimal.valueOf(500));
        statement.setPaidAmount(BigDecimal.valueOf(500));
        statement.setDueDate(LocalDate.of(2026, 1, 1));
        statement.setInterestCharged(BigDecimal.ZERO);

        when(statementRepository.findFirstByCardIdOrderByStatementDateDesc(5L)).thenReturn(Optional.of(statement));

        postingService.chargeInterestIfOverdue(card, LocalDate.of(2026, 2, 1));

        verify(cardRepository, never()).save(any());
    }

    @Test
    void chargeInterestIfOverdue_shouldNotChargeTwice_whenAlreadyCharged() {
        CreditCardStatement statement = new CreditCardStatement();
        statement.setCard(card);
        statement.setStatementBalance(BigDecimal.valueOf(500));
        statement.setPaidAmount(BigDecimal.ZERO);
        statement.setDueDate(LocalDate.of(2026, 1, 1));
        statement.setInterestCharged(BigDecimal.valueOf(10));

        when(statementRepository.findFirstByCardIdOrderByStatementDateDesc(5L)).thenReturn(Optional.of(statement));

        postingService.chargeInterestIfOverdue(card, LocalDate.of(2026, 2, 1));

        verify(cardRepository, never()).save(any());
    }
}
