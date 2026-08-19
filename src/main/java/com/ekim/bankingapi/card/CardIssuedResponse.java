package com.ekim.bankingapi.card;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class CardIssuedResponse {

    private Long id;
    private Long accountId;
    private String cardNumber;
    private String cvv;
    private String cardHolderName;
    private LocalDate expiryDate;
    private CardStatus status;
    private CardType cardType;
    private BigDecimal creditLimit;

    public static CardIssuedResponse of(Card card, String cardNumber, String cvv) {
        return new CardIssuedResponse(
                card.getId(),
                card.getAccount().getId(),
                cardNumber,
                cvv,
                card.getCardHolderName(),
                card.getExpiryDate(),
                card.getStatus(),
                card.getCardType(),
                card.getCreditLimit()
        );
    }
}
