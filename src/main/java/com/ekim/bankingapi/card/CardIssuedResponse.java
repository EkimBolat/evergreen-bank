package com.ekim.bankingapi.card;

import lombok.AllArgsConstructor;
import lombok.Getter;

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

    public static CardIssuedResponse of(Card card, String cardNumber, String cvv) {
        return new CardIssuedResponse(
                card.getId(),
                card.getAccount().getId(),
                cardNumber,
                cvv,
                card.getCardHolderName(),
                card.getExpiryDate(),
                card.getStatus()
        );
    }
}
