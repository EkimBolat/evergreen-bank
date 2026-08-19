package com.ekim.bankingapi.card;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditCardBillingServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CreditCardStatementPostingService postingService;

    @InjectMocks
    private CreditCardBillingService billingService;

    @Test
    void runDailyBilling_shouldGenerateStatement_whenNextStatementDateIsDue() {
        Card dueCard = new Card();
        dueCard.setId(1L);
        dueCard.setNextStatementDate(LocalDate.now());

        when(cardRepository.findByCardTypeAndStatus(CardType.CREDIT, CardStatus.ACTIVE)).thenReturn(List.of(dueCard));

        billingService.runDailyBilling();

        verify(postingService).chargeInterestIfOverdue(eq(dueCard), any(LocalDate.class));
        verify(postingService).generateStatement(eq(dueCard), any(LocalDate.class));
    }

    @Test
    void runDailyBilling_shouldNotGenerateStatement_whenNotYetDue() {
        Card notDueCard = new Card();
        notDueCard.setId(2L);
        notDueCard.setNextStatementDate(LocalDate.now().plusDays(10));

        when(cardRepository.findByCardTypeAndStatus(CardType.CREDIT, CardStatus.ACTIVE)).thenReturn(List.of(notDueCard));

        billingService.runDailyBilling();

        verify(postingService).chargeInterestIfOverdue(eq(notDueCard), any(LocalDate.class));
        verify(postingService, never()).generateStatement(any(), any());
    }

    @Test
    void runDailyBilling_shouldContinueProcessing_whenOneCardFails() {
        Card failingCard = new Card();
        failingCard.setId(3L);
        failingCard.setNextStatementDate(LocalDate.now());

        Card healthyCard = new Card();
        healthyCard.setId(4L);
        healthyCard.setNextStatementDate(LocalDate.now());

        when(cardRepository.findByCardTypeAndStatus(CardType.CREDIT, CardStatus.ACTIVE))
                .thenReturn(List.of(failingCard, healthyCard));
        doThrow(new RuntimeException("boom")).when(postingService).chargeInterestIfOverdue(eq(failingCard), any());

        billingService.runDailyBilling();

        verify(postingService).chargeInterestIfOverdue(eq(healthyCard), any(LocalDate.class));
        verify(postingService).generateStatement(eq(healthyCard), any(LocalDate.class));
    }
}
