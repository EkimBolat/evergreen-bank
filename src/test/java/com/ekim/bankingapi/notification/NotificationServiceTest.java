package com.ekim.bankingapi.notification;

import com.ekim.bankingapi.auth.User;
import com.ekim.bankingapi.auth.UserRepository;
import com.ekim.bankingapi.customer.Customer;
import com.ekim.bankingapi.customer.CustomerService;
import com.ekim.bankingapi.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationService notificationService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId(1L);

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken("ahmet@example.com", null, List.of());
        authToken.setDetails(1L);
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void notify_shouldSaveNotification_forGivenCustomer() {
        when(customerService.findCustomerEntityById(1L)).thenReturn(customer);

        notificationService.notify(1L, NotificationType.DEPOSIT, "Deposit Received", "You received 100");

        verify(notificationRepository).save(argThat(n ->
                n.getCustomer().equals(customer)
                        && n.getType() == NotificationType.DEPOSIT
                        && n.getTitle().equals("Deposit Received")
        ));
    }

    @Test
    void notify_shouldPushOverWebSocket_whenCustomerHasLoginAccount() {
        Notification saved = new Notification();
        saved.setId(10L);
        saved.setCustomer(customer);
        saved.setType(NotificationType.DEPOSIT);
        saved.setTitle("Deposit Received");
        saved.setMessage("You received 100");

        User user = new User();
        user.setEmail("ahmet@example.com");

        when(customerService.findCustomerEntityById(1L)).thenReturn(customer);
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);
        when(userRepository.findByCustomerId(1L)).thenReturn(Optional.of(user));

        notificationService.notify(1L, NotificationType.DEPOSIT, "Deposit Received", "You received 100");

        verify(messagingTemplate).convertAndSendToUser(
                eq("ahmet@example.com"), eq("/queue/notifications"), any(NotificationResponse.class));
    }

    @Test
    void notify_shouldNotPush_whenCustomerHasNoLoginAccount() {
        when(customerService.findCustomerEntityById(1L)).thenReturn(customer);
        when(userRepository.findByCustomerId(1L)).thenReturn(Optional.empty());

        notificationService.notify(1L, NotificationType.DEPOSIT, "Deposit Received", "You received 100");

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void getMyNotifications_shouldReturnPagedNotifications_forCurrentCustomer() {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setCustomer(customer);
        notification.setType(NotificationType.DEPOSIT);
        notification.setTitle("Deposit Received");
        notification.setMessage("You received 100");

        when(notificationRepository.findByCustomerId(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(notification)));

        Page<NotificationResponse> result = notificationService.getMyNotifications(PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Deposit Received");
    }

    @Test
    void getUnreadCount_shouldReturnCount_forCurrentCustomer() {
        when(notificationRepository.countByCustomerIdAndIsReadFalse(1L)).thenReturn(3L);

        assertThat(notificationService.getUnreadCount()).isEqualTo(3L);
    }

    @Test
    void markAsRead_shouldMarkNotificationAsRead_whenBelongsToCurrentCustomer() {
        Notification notification = new Notification();
        notification.setId(5L);
        notification.setCustomer(customer);
        notification.setType(NotificationType.DEPOSIT);

        when(notificationRepository.findByIdAndCustomerId(5L, 1L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(5L);

        assertThat(response.isRead()).isTrue();
    }

    @Test
    void markAsRead_shouldThrow_whenNotificationNotFoundForCustomer() {
        when(notificationRepository.findByIdAndCustomerId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markAllAsRead_shouldDelegateToRepository_forCurrentCustomer() {
        notificationService.markAllAsRead();

        verify(notificationRepository).markAllAsReadForCustomer(1L);
    }
}
