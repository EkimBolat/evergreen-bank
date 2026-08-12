package com.ekim.bankingapi.notification;

import com.ekim.bankingapi.auth.UserRepository;
import com.ekim.bankingapi.customer.Customer;
import com.ekim.bankingapi.customer.CustomerService;
import com.ekim.bankingapi.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CustomerService customerService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public void notify(Long customerId, NotificationType type, String title, String message) {
        Customer customer = customerService.findCustomerEntityById(customerId);

        Notification notification = new Notification();
        notification.setCustomer(customer);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        Notification saved = notificationRepository.save(notification);

        userRepository.findByCustomerId(customerId).ifPresent(user ->
                messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/notifications",
                        NotificationResponse.fromEntity(saved)));
    }

    public Page<NotificationResponse> getMyNotifications(Pageable pageable) {
        return notificationRepository.findByCustomerId(currentCustomerId(), pageable)
                .map(NotificationResponse::fromEntity);
    }

    public long getUnreadCount() {
        return notificationRepository.countByCustomerIdAndIsReadFalse(currentCustomerId());
    }

    @Transactional
    public NotificationResponse markAsRead(Long id) {
        Notification notification = notificationRepository.findByIdAndCustomerId(id, currentCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        notification.setRead(true);
        return NotificationResponse.fromEntity(notificationRepository.save(notification));
    }

    @Transactional
    public void markAllAsRead() {
        notificationRepository.markAllAsReadForCustomer(currentCustomerId());
    }

    private Long currentCustomerId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getDetails();
    }
}
