package com.ekim.bankingapi.card;

import com.ekim.bankingapi.account.Account;
import com.ekim.bankingapi.account.AccountService;
import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.exception.InvalidCredentialsException;
import com.ekim.bankingapi.exception.InvalidRequestException;
import com.ekim.bankingapi.exception.ResourceNotFoundException;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CardService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CARD_VALIDITY_YEARS = 5;
    static final BigDecimal DEFAULT_CREDIT_APR = BigDecimal.valueOf(42.00);

    private final CardRepository cardRepository;
    private final AccountService accountService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public CardIssuedResponse issueCard(Long accountId, CardIssueRequest request) {
        Account account = accountService.requireOwnedAccount(accountId);

        CardType cardType = request.getCardType() == null ? CardType.DEBIT : request.getCardType();
        if (cardType == CardType.CREDIT && request.getCreditLimit() == null) {
            throw new InvalidRequestException("Credit limit is required for credit cards");
        }

        String cardNumber = generateUniqueCardNumber();
        String cvv = generateCvv();

        Card card = new Card();
        card.setAccount(account);
        card.setCardNumber(cardNumber);
        card.setCardHolderName((account.getCustomer().getFirstName() + " " + account.getCustomer().getLastName()).toUpperCase(Locale.ROOT));
        card.setExpiryDate(LocalDate.now().plusYears(CARD_VALIDITY_YEARS));
        card.setStatus(CardStatus.ACTIVE);
        card.setCardType(cardType);

        if (cardType == CardType.CREDIT) {
            card.setCreditLimit(request.getCreditLimit());
            card.setCurrentBalance(BigDecimal.ZERO);
            card.setApr(DEFAULT_CREDIT_APR);
            card.setNextStatementDate(LocalDate.now().plusMonths(1));
        }

        Card saved = cardRepository.save(card);

        auditLogService.log("Card", saved.getId(), "ISSUE",
                "Card issued for account: " + account.getAccountNumber() + ", type: " + cardType);

        notificationService.notify(account.getCustomer().getId(), NotificationType.CARD_ISSUED,
                "New Card Issued", "A new " + cardType.name().toLowerCase(Locale.ROOT) + " card ending in " +
                        cardNumber.substring(cardNumber.length() - 4) + " was issued for account " + account.getAccountNumber());

        return CardIssuedResponse.of(saved, cardNumber, cvv);
    }

    public List<CardResponse> getCardsForAccount(Long accountId) {
        accountService.requireOwnedAccount(accountId);
        return cardRepository.findByAccountId(accountId).stream()
                .map(CardResponse::fromEntity)
                .toList();
    }

    public CardResponse getCardById(Long id) {
        return CardResponse.fromEntity(findCardEntityById(id));
    }

    public CardResponse blockCard(Long id) {
        Card card = requireOwnedCard(id);

        if (card.getStatus() == CardStatus.CANCELLED) {
            throw new InvalidRequestException("Cancelled cards cannot be blocked");
        }
        if (card.getStatus() == CardStatus.BLOCKED) {
            throw new InvalidRequestException("Card is already blocked");
        }

        card.setStatus(CardStatus.BLOCKED);
        Card saved = cardRepository.save(card);

        auditLogService.log("Card", saved.getId(), "BLOCK", "Card blocked: " + maskedNumber(saved));

        notificationService.notify(saved.getAccount().getCustomer().getId(), NotificationType.CARD_BLOCKED,
                "Card Blocked", "Your card ending in " + lastFour(saved) + " has been blocked");

        return CardResponse.fromEntity(saved);
    }

    public CardResponse activateCard(Long id) {
        Card card = requireOwnedCard(id);

        if (card.getStatus() == CardStatus.CANCELLED) {
            throw new InvalidRequestException("Cancelled cards cannot be reactivated");
        }
        if (card.getStatus() == CardStatus.ACTIVE) {
            throw new InvalidRequestException("Card is already active");
        }

        card.setStatus(CardStatus.ACTIVE);
        Card saved = cardRepository.save(card);

        auditLogService.log("Card", saved.getId(), "ACTIVATE", "Card activated: " + maskedNumber(saved));

        return CardResponse.fromEntity(saved);
    }

    public CardResponse cancelCard(Long id) {
        Card card = requireOwnedCard(id);

        if (card.getStatus() == CardStatus.CANCELLED) {
            throw new InvalidRequestException("Card is already cancelled");
        }

        card.setStatus(CardStatus.CANCELLED);
        Card saved = cardRepository.save(card);

        auditLogService.log("Card", saved.getId(), "CANCEL", "Card permanently cancelled: " + maskedNumber(saved));

        notificationService.notify(saved.getAccount().getCustomer().getId(), NotificationType.CARD_CANCELLED,
                "Card Cancelled", "Your card ending in " + lastFour(saved) + " has been permanently cancelled");

        return CardResponse.fromEntity(saved);
    }

    Card findCardEntityById(Long id) {
        return cardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found with id: " + id));
    }

    Card requireOwnedCard(Long id) {
        Card card = findCardEntityById(id);
        if (!card.getAccount().getCustomer().getId().equals(currentCustomerId())) {
            throw new InvalidCredentialsException("You do not have access to this card");
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

    private String generateUniqueCardNumber() {
        String candidate;
        do {
            candidate = generateRandomCardNumber();
        } while (cardRepository.existsByCardNumber(candidate));
        return candidate;
    }

    private String generateRandomCardNumber() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private String generateCvv() {
        return String.format("%03d", RANDOM.nextInt(1000));
    }

    private String lastFour(Card card) {
        return card.getCardNumber().substring(card.getCardNumber().length() - 4);
    }

    private String maskedNumber(Card card) {
        return "**** **** **** " + lastFour(card);
    }
}
