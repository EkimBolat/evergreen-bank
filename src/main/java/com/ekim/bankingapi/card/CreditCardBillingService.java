package com.ekim.bankingapi.card;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditCardBillingService {

    private final CardRepository cardRepository;
    private final CreditCardStatementPostingService postingService;

    @Scheduled(cron = "0 0 3 * * *")
    public void runDailyBilling() {
        List<Card> activeCreditCards = cardRepository.findByCardTypeAndStatus(CardType.CREDIT, CardStatus.ACTIVE);
        LocalDate today = LocalDate.now();

        log.info("Running credit card billing for {} active card(s)", activeCreditCards.size());

        for (Card card : activeCreditCards) {
            try {
                postingService.chargeInterestIfOverdue(card, today);

                if (card.getNextStatementDate() != null && !card.getNextStatementDate().isAfter(today)) {
                    postingService.generateStatement(card, today);
                }
            } catch (Exception e) {
                log.error("Credit card billing failed: cardId={}, reason={}", card.getId(), e.getMessage());
            }
        }
    }
}
