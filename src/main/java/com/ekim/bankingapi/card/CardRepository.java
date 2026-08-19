package com.ekim.bankingapi.card;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByAccountId(Long accountId);

    Optional<Card> findByCardNumber(String cardNumber);

    boolean existsByCardNumber(String cardNumber);

    List<Card> findByCardTypeAndStatus(CardType cardType, CardStatus status);
}
