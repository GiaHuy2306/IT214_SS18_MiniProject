package com.rikkeibank.notification.repository;

import com.rikkeibank.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientAccountNumberOrderByCreatedAtDesc(String accountNumber);
    List<Notification> findByTransactionReference(String reference);
}
