package com.ekim.bankingapi.card;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CreditCardStatementRepository extends JpaRepository<CreditCardStatement, Long> {

    List<CreditCardStatement> findByCardIdOrderByStatementDateDesc(Long cardId);

    Optional<CreditCardStatement> findFirstByCardIdOrderByStatementDateDesc(Long cardId);
}
