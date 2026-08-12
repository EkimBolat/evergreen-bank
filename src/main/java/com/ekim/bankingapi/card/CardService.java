package com.ekim.bankingapi.card;

import com.ekim.bankingapi.account.Account;
import com.ekim.bankingapi.account.AccountService;
import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.exception.InvalidRequestException;
import com.ekim.bankingapi.exception.ResourceNotFoundException;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CardService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CARD_VALIDITY_YEARS = 5;

    private final CardRepository cardRepository;
    private final AccountService accountService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    public CardIssuedResponse issueCard(Long accountId) {
        Account account = accountService.findAccountEntityById(accountId);

        String cardNumber = generateUniqueCardNumber();
        String cvv = generateCvv();

        Card card = new Card();
        card.setAccount(account);
        card.setCardNumber(cardNumber);
        card.setCardHolderName((account.getCustomer().getFirstName() + " " + account.getCustomer().getLastName()).toUpperCase(Locale.ROOT));
        card.setExpiryDate(LocalDate.now().plusYears(CARD_VALIDITY_YEARS));
        card.setStatus(CardStatus.ACTIVE);

        Card saved = cardRepository.save(card);

        auditLogService.log("Card", saved.getId(), "ISSUE", "Card issued for account: " + account.getAccountNumber());

        notificationService.notify(account.getCustomer().getId(), NotificationType.CARD_ISSUED,
                "New Card Issued", "A new card ending in " + cardNumber.substring(cardNumber.length() - 4) +
                        " was issued for account " + account.getAccountNumber());

        return CardIssuedResponse.of(saved, cardNumber, cvv);
    }

    public List<CardResponse> getCardsForAccount(Long accountId) {
        accountService.findAccountEntityById(accountId);
        return cardRepository.findByAccountId(accountId).stream()
                .map(CardResponse::fromEntity)
                .toList();
    }

    public CardResponse getCardById(Long id) {
        return CardResponse.fromEntity(findCardEntityById(id));
    }

    public CardResponse blockCard(Long id) {
        Card card = findCardEntityById(id);

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
        Card card = findCardEntityById(id);

        if (card.getStatus() == CardStatus.ACTIVE) {
            throw new InvalidRequestException("Card is already active");
        }

        card.setStatus(CardStatus.ACTIVE);
        Card saved = cardRepository.save(card);

        auditLogService.log("Card", saved.getId(), "ACTIVATE", "Card activated: " + maskedNumber(saved));

        return CardResponse.fromEntity(saved);
    }

    private Card findCardEntityById(Long id) {
        return cardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found with id: " + id));
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
