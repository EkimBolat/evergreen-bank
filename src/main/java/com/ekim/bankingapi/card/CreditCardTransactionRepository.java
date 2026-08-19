package com.ekim.bankingapi.card;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditCardTransactionRepository extends JpaRepository<CreditCardTransaction, Long> {

    List<CreditCardTransaction> findByCardIdOrderByTimestampDesc(Long cardId);
}
