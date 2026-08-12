package com.ekim.bankingapi.card;

import com.ekim.bankingapi.account.Account;
import com.ekim.bankingapi.account.AccountService;
import com.ekim.bankingapi.audit.AuditLogService;
import com.ekim.bankingapi.customer.Customer;
import com.ekim.bankingapi.exception.InvalidRequestException;
import com.ekim.bankingapi.exception.ResourceNotFoundException;
import com.ekim.bankingapi.notification.NotificationService;
import com.ekim.bankingapi.notification.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CardService cardService;

    private Account account;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setFirstName("Ahmet");
        customer.setLastName("Yilmaz");

        account = new Account();
        account.setId(1L);
        account.setAccountNumber("TR1111111111");
        account.setCustomer(customer);
    }

    @Test
    void issueCard_shouldCreateCard_whenAccountExists() {
        when(accountService.findAccountEntityById(1L)).thenReturn(account);
        when(cardRepository.existsByCardNumber(anyString())).thenReturn(false);
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> {
            Card c = invocation.getArgument(0);
            c.setId(10L);
            return c;
        });

        CardIssuedResponse response = cardService.issueCard(1L);

        assertThat(response.getCardNumber()).hasSize(16);
        assertThat(response.getCvv()).hasSize(3);
        assertThat(response.getCardHolderName()).isEqualTo("AHMET YILMAZ");
        assertThat(response.getStatus()).isEqualTo(CardStatus.ACTIVE);

        verify(notificationService).notify(eq(1L), eq(NotificationType.CARD_ISSUED), anyString(), anyString());
    }

    @Test
    void getCardsForAccount_shouldReturnMaskedCards() {
        Card card = new Card();
        card.setId(1L);
        card.setAccount(account);
        card.setCardNumber("1234567812345678");
        card.setCardHolderName("AHMET YILMAZ");
        card.setStatus(CardStatus.ACTIVE);

        when(accountService.findAccountEntityById(1L)).thenReturn(account);
        when(cardRepository.findByAccountId(1L)).thenReturn(List.of(card));

        List<CardResponse> responses = cardService.getCardsForAccount(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getMaskedCardNumber()).isEqualTo("**** **** **** 5678");
    }

    @Test
    void getCardById_shouldThrow_whenNotFound() {
        when(cardRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getCardById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void blockCard_shouldSetStatusBlocked_andNotifyCustomer() {
        Card card = new Card();
        card.setId(1L);
        card.setAccount(account);
        card.setCardNumber("1234567812345678");
        card.setStatus(CardStatus.ACTIVE);

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardResponse response = cardService.blockCard(1L);

        assertThat(response.getStatus()).isEqualTo(CardStatus.BLOCKED);
        verify(notificationService).notify(eq(1L), eq(NotificationType.CARD_BLOCKED), anyString(), anyString());
    }

    @Test
    void blockCard_shouldThrow_whenAlreadyBlocked() {
        Card card = new Card();
        card.setId(1L);
        card.setAccount(account);
        card.setCardNumber("1234567812345678");
        card.setStatus(CardStatus.BLOCKED);

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> cardService.blockCard(1L))
                .isInstanceOf(InvalidRequestException.class);

        verify(cardRepository, never()).save(any());
    }

    @Test
    void activateCard_shouldSetStatusActive_whenCurrentlyBlocked() {
        Card card = new Card();
        card.setId(1L);
        card.setAccount(account);
        card.setCardNumber("1234567812345678");
        card.setStatus(CardStatus.BLOCKED);

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardResponse response = cardService.activateCard(1L);

        assertThat(response.getStatus()).isEqualTo(CardStatus.ACTIVE);
    }

    @Test
    void activateCard_shouldThrow_whenAlreadyActive() {
        Card card = new Card();
        card.setId(1L);
        card.setAccount(account);
        card.setCardNumber("1234567812345678");
        card.setStatus(CardStatus.ACTIVE);

        when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

        assertThatThrownBy(() -> cardService.activateCard(1L))
                .isInstanceOf(InvalidRequestException.class);

        verify(cardRepository, never()).save(any());
    }
}
