package com.ekim.bankingapi.card;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CardResponse {

    private Long id;
    private Long accountId;
    private String maskedCardNumber;
    private String cardHolderName;
    private LocalDate expiryDate;
    private CardStatus status;
    private CardType cardType;
    private BigDecimal creditLimit;
    private BigDecimal currentBalance;
    private BigDecimal availableCredit;
    private LocalDateTime createdAt;

    public static CardResponse fromEntity(Card card) {
        boolean isCredit = card.getCardType() == CardType.CREDIT;
        return new CardResponse(
                card.getId(),
                card.getAccount().getId(),
                mask(card.getCardNumber()),
                card.getCardHolderName(),
                card.getExpiryDate(),
                card.getStatus(),
                card.getCardType(),
                card.getCreditLimit(),
                card.getCurrentBalance(),
                isCredit ? card.getCreditLimit().subtract(card.getCurrentBalance()) : null,
                card.getCreatedAt()
        );
    }

    private static String mask(String cardNumber) {
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }
}
