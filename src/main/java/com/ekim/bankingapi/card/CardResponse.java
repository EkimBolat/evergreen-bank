package com.ekim.bankingapi.card;

import lombok.AllArgsConstructor;
import lombok.Getter;

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
    private LocalDateTime createdAt;

    public static CardResponse fromEntity(Card card) {
        return new CardResponse(
                card.getId(),
                card.getAccount().getId(),
                mask(card.getCardNumber()),
                card.getCardHolderName(),
                card.getExpiryDate(),
                card.getStatus(),
                card.getCreatedAt()
        );
    }

    private static String mask(String cardNumber) {
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }
}
