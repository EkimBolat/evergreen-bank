package com.ekim.bankingapi.card;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class CreditCardStatementResponse {

    private Long id;
    private LocalDate statementDate;
    private LocalDate dueDate;
    private BigDecimal statementBalance;
    private BigDecimal minimumPayment;
    private BigDecimal paidAmount;
    private BigDecimal interestCharged;
    private boolean paidInFull;

    public static CreditCardStatementResponse fromEntity(CreditCardStatement statement) {
        return new CreditCardStatementResponse(
                statement.getId(),
                statement.getStatementDate(),
                statement.getDueDate(),
                statement.getStatementBalance(),
                statement.getMinimumPayment(),
                statement.getPaidAmount(),
                statement.getInterestCharged(),
                statement.getPaidAmount().compareTo(statement.getStatementBalance()) >= 0
        );
    }
}
