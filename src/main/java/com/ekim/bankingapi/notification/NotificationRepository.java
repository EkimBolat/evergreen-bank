package com.ekim.bankingapi.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByCustomerId(Long customerId, Pageable pageable);

    Optional<Notification> findByIdAndCustomerId(Long id, Long customerId);

    long countByCustomerIdAndIsReadFalse(Long customerId);

    @Modifying
    @Query("update Notification n set n.isRead = true where n.customer.id = :customerId and n.isRead = false")
    void markAllAsReadForCustomer(@Param("customerId") Long customerId);
}
