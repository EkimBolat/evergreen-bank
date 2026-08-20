package com.ekim.bankingapi.card;

import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.exception.InvalidCredentialsException;
import com.ekim.bankingapi.exception.InvalidRequestException;
import com.ekim.bankingapi.exception.ResourceNotFoundException;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.notification.NotificationType;
import com.ekim.bankingapi.transaction.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreditCardService {

    private final CardRepository cardRepository;
    private final CreditCardTransactionRepository creditCardTransactionRepository;
    private final CreditCardStatementRepository creditCardStatementRepository;
    private final TransactionService transactionService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    @Transactional
    public CreditCardTransactionResponse charge(Long cardId, CreditChargeRequest request) {
        Card card = findCreditCard(cardId);
        requireActive(card);

        BigDecimal available = card.getCreditLimit().subtract(card.getCurrentBalance());
        if (request.getAmount().compareTo(available) > 0) {
            throw new InvalidRequestException("Amount exceeds available credit. Available: " + available);
        }

        BigDecimal newBalance = card.getCurrentBalance().add(request.getAmount());
        card.setCurrentBalance(newBalance);
        cardRepository.save(card);

        CreditCardTransaction saved = saveTransaction(card, CreditCardTransactionType.PURCHASE,
                request.getAmount(), request.getDescription(), newBalance);

        auditLogService.log("CreditCard", card.getId(), "CHARGE", "Amount: " + request.getAmount());
        notificationService.notify(card.getAccount().getCustomer().getId(), NotificationType.CREDIT_CARD_CHARGED,
                "Credit Card Charge", "A charge of " + request.getAmount() + " was made on your card ending in " + lastFour(card));

        return CreditCardTransactionResponse.fromEntity(saved);
    }

    @Transactional
    public CreditCardTransactionResponse pay(Long cardId, CreditPaymentRequest request) {
        Card card = findCreditCard(cardId);
        BigDecimal amount = request.getAmount();

        if (amount.compareTo(card.getCurrentBalance()) > 0) {
            throw new InvalidRequestException("Payment amount exceeds current balance");
        }

        transactionService.withdraw(card.getAccount().getId(), amount);

        BigDecimal newBalance = card.getCurrentBalance().subtract(amount);
        card.setCurrentBalance(newBalance);
        cardRepository.save(card);

        creditCardStatementRepository.findFirstByCardIdOrderByStatementDateDesc(card.getId())
                .ifPresent(statement -> {
                    BigDecimal remaining = statement.getStatementBalance().subtract(statement.getPaidAmount());
                    if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal applied = amount.min(remaining);
                        statement.setPaidAmount(statement.getPaidAmount().add(applied));
                        creditCardStatementRepository.save(statement);
                    }
                });

        CreditCardTransaction saved = saveTransaction(card, CreditCardTransactionType.PAYMENT, amount, null, newBalance);

        auditLogService.log("CreditCard", card.getId(), "PAYMENT", "Amount: " + amount);
        notificationService.notify(card.getAccount().getCustomer().getId(), NotificationType.CREDIT_CARD_PAYMENT_RECEIVED,
                "Credit Card Payment", "A payment of " + amount + " was applied to your card ending in " + lastFour(card));

        return CreditCardTransactionResponse.fromEntity(saved);
    }

    public List<CreditCardStatementResponse> getStatements(Long cardId) {
        findCreditCard(cardId);
        return creditCardStatementRepository.findByCardIdOrderByStatementDateDesc(cardId).stream()
                .map(CreditCardStatementResponse::fromEntity)
                .toList();
    }

    public List<CreditCardTransactionResponse> getTransactions(Long cardId) {
        findCreditCard(cardId);
        return creditCardTransactionRepository.findByCardIdOrderByTimestampDesc(cardId).stream()
                .map(CreditCardTransactionResponse::fromEntity)
                .toList();
    }

    private CreditCardTransaction saveTransaction(Card card, CreditCardTransactionType type, BigDecimal amount,
                                                   String description, BigDecimal balanceAfter) {
        CreditCardTransaction transaction = new CreditCardTransaction();
        transaction.setCard(card);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transaction.setBalanceAfter(balanceAfter);
        return creditCardTransactionRepository.save(transaction);
    }

    private Card findCreditCard(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found with id: " + cardId));
        if (!card.getAccount().getCustomer().getId().equals(currentCustomerId())) {
            throw new InvalidCredentialsException("You do not have access to this card");
        }
        if (card.getCardType() != CardType.CREDIT) {
            throw new InvalidRequestException("This operation is only valid for credit cards");
        }
        return card;
    }

    private Long currentCustomerId() {
        Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
        if (!(details instanceof Long customerId)) {
            throw new InvalidCredentialsException("Unable to resolve authenticated customer");
        }
        return customerId;
    }

    private void requireActive(Card card) {
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new InvalidRequestException("Card is not active");
        }
    }

    private String lastFour(Card card) {
        return card.getCardNumber().substring(card.getCardNumber().length() - 4);
    }
}
