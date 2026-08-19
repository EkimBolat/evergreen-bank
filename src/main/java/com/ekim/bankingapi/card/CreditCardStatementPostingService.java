package com.ekim.bankingapi.card;

import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Holds the per-card billing logic as its own bean so {@code @Transactional} is
 * honored — {@link CreditCardBillingService} calls into this bean from its scheduled
 * loop, which goes through the Spring proxy (unlike a same-class self-invocation would).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditCardStatementPostingService {

    private static final int DUE_DAYS_AFTER_STATEMENT = 25;
    private static final BigDecimal MIN_PAYMENT_PERCENTAGE = BigDecimal.valueOf(0.03);
    private static final BigDecimal MIN_PAYMENT_FLOOR = BigDecimal.valueOf(50);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    private final CardRepository cardRepository;
    private final CreditCardStatementRepository statementRepository;
    private final CreditCardTransactionRepository transactionRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    @Transactional
    public void chargeInterestIfOverdue(Card card, LocalDate today) {
        statementRepository.findFirstByCardIdOrderByStatementDateDesc(card.getId()).ifPresent(statement -> {
            BigDecimal outstanding = statement.getStatementBalance().subtract(statement.getPaidAmount());
            boolean alreadyCharged = statement.getInterestCharged().compareTo(BigDecimal.ZERO) > 0;

            if (outstanding.compareTo(BigDecimal.ZERO) <= 0 || !statement.getDueDate().isBefore(today) || alreadyCharged) {
                return;
            }

            BigDecimal monthlyRate = card.getApr()
                    .divide(HUNDRED, 10, RoundingMode.HALF_UP)
                    .divide(MONTHS_PER_YEAR, 10, RoundingMode.HALF_UP);
            BigDecimal interest = outstanding.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);

            if (interest.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            BigDecimal newBalance = card.getCurrentBalance().add(interest);
            card.setCurrentBalance(newBalance);
            cardRepository.save(card);

            statement.setInterestCharged(interest);
            statementRepository.save(statement);

            CreditCardTransaction transaction = new CreditCardTransaction();
            transaction.setCard(card);
            transaction.setType(CreditCardTransactionType.INTEREST_CHARGE);
            transaction.setAmount(interest);
            transaction.setDescription("Late payment interest");
            transaction.setBalanceAfter(newBalance);
            transactionRepository.save(transaction);

            auditLogService.log("CreditCard", card.getId(), "INTEREST_CHARGE", "Interest: " + interest);
            notificationService.notify(card.getAccount().getCustomer().getId(), NotificationType.CREDIT_CARD_INTEREST_CHARGED,
                    "Interest Charged", "Interest of " + interest + " was charged on the overdue balance of your card ending in " +
                            lastFour(card));

            log.info("Interest charged: cardId={}, interest={}", card.getId(), interest);
        });
    }

    @Transactional
    public void generateStatement(Card card, LocalDate today) {
        CreditCardStatement statement = new CreditCardStatement();
        statement.setCard(card);
        statement.setStatementDate(today);
        statement.setDueDate(today.plusDays(DUE_DAYS_AFTER_STATEMENT));
        statement.setStatementBalance(card.getCurrentBalance());

        BigDecimal minimumPayment;
        if (card.getCurrentBalance().compareTo(BigDecimal.ZERO) <= 0) {
            minimumPayment = BigDecimal.ZERO;
        } else {
            BigDecimal percentageBased = card.getCurrentBalance().multiply(MIN_PAYMENT_PERCENTAGE).setScale(2, RoundingMode.HALF_UP);
            minimumPayment = percentageBased.max(MIN_PAYMENT_FLOOR).min(card.getCurrentBalance());
        }
        statement.setMinimumPayment(minimumPayment);
        statementRepository.save(statement);

        card.setNextStatementDate(today.plusMonths(1));
        cardRepository.save(card);

        auditLogService.log("CreditCard", card.getId(), "STATEMENT_GENERATED", "Balance: " + statement.getStatementBalance());
        notificationService.notify(card.getAccount().getCustomer().getId(), NotificationType.CREDIT_STATEMENT_GENERATED,
                "New Statement Ready", "Your statement for card ending in " + lastFour(card) + " is ready. Balance: " +
                        statement.getStatementBalance() + ", minimum payment: " + statement.getMinimumPayment() +
                        ", due " + statement.getDueDate());

        log.info("Statement generated: cardId={}, balance={}, dueDate={}", card.getId(), statement.getStatementBalance(), statement.getDueDate());
    }

    private String lastFour(Card card) {
        return card.getCardNumber().substring(card.getCardNumber().length() - 4);
    }
}
