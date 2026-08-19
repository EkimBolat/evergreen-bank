package com.ekim.bankingapi.card;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CardIssueRequest {

    private CardType cardType = CardType.DEBIT;

    @DecimalMin(value = "0.01", message = "Credit limit must be greater than zero")
    private BigDecimal creditLimit;
}
