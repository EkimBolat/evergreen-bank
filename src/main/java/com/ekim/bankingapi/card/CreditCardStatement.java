package com.ekim.bankingapi.card;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_card_statements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreditCardStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Column(nullable = false, updatable = false)
    private LocalDate statementDate;

    @Column(nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(nullable = false, updatable = false)
    private BigDecimal statementBalance;

    @Column(nullable = false, updatable = false)
    private BigDecimal minimumPayment;

    @Column(nullable = false)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal interestCharged = BigDecimal.ZERO;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
