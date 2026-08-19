package com.ekim.bankingapi.card;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CreditCardTransactionResponse {

    private Long id;
    private CreditCardTransactionType type;
    private BigDecimal amount;
    private String description;
    private BigDecimal balanceAfter;
    private LocalDateTime timestamp;

    public static CreditCardTransactionResponse fromEntity(CreditCardTransaction transaction) {
        return new CreditCardTransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getBalanceAfter(),
                transaction.getTimestamp()
        );
    }
}
