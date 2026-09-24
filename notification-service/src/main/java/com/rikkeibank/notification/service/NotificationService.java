package com.rikkeibank.notification.service;

import com.rikkeibank.common.event.TransactionEvent;
import com.rikkeibank.notification.entity.Notification;
import com.rikkeibank.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Asynchronous Event-driven Kafka Consumer
     * Consumes events from topic 'transaction-events'
     */
    @KafkaListener(topics = "transaction-events", groupId = "rikkeibank-notification-group", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void handleTransactionEvent(TransactionEvent event) {
        log.info("--- [KAFKA EVENT CONSUMED] Received event: {} for Ref: {} ---",
                event.getEventType(), event.getTransactionReference());
        processEvent(event);
    }

    @Transactional
    public void processEvent(TransactionEvent event) {
        if ("TRANSFER_COMPLETED".equals(event.getEventType())) {
            // 1. Alert to Sender (Debit alert)
            Notification senderNotification = Notification.builder()
                    .recipientAccountNumber(event.getSourceAccountNumber())
                    .title("Biến động số dư: Trừ tiền thành công")
                    .message(String.format("Tài khoản %s: -%s %s. Giao dịch chuyển khoản đến %s thành công. Mã GD: %s. Nội dung: %s",
                            event.getSourceAccountNumber(),
                            event.getAmount(),
                            event.getCurrency(),
                            event.getTargetAccountNumber(),
                            event.getTransactionReference(),
                            event.getDescription()))
                    .transactionReference(event.getTransactionReference())
                    .type("DEBIT_ALERT")
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(senderNotification);

            // 2. Alert to Receiver (Credit alert)
            Notification receiverNotification = Notification.builder()
                    .recipientAccountNumber(event.getTargetAccountNumber())
                    .title("Biến động số dư: Nhận tiền thành công")
                    .message(String.format("Tài khoản %s: +%s %s từ tài khoản %s. Mã GD: %s. Nội dung: %s",
                            event.getTargetAccountNumber(),
                            event.getAmount(),
                            event.getCurrency(),
                            event.getSourceAccountNumber(),
                            event.getTransactionReference(),
                            event.getDescription()))
                    .transactionReference(event.getTransactionReference())
                    .type("CREDIT_ALERT")
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(receiverNotification);

            log.info("Generated Debit & Credit notifications for transaction: {}", event.getTransactionReference());

        } else if ("TRANSFER_FAILED_COMPENSATED".equals(event.getEventType())) {
            // Alert for Failed & Compensated transfer
            Notification rollbackNotification = Notification.builder()
                    .recipientAccountNumber(event.getSourceAccountNumber())
                    .title("Thông báo bồi hoàn giao dịch chuyển khoản")
                    .message(String.format("Giao dịch chuyển khoản %s %s tới tài khoản %s thất bại (%s). Hệ thống đã tự động hoàn trả số dư về tài khoản của bạn.",
                            event.getAmount(),
                            event.getCurrency(),
                            event.getTargetAccountNumber(),
                            event.getFailureReason()))
                    .transactionReference(event.getTransactionReference())
                    .type("ROLLBACK_ALERT")
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(rollbackNotification);

            log.warn("Generated Rollback Compensating notification for transaction: {}", event.getTransactionReference());
        }
    }

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }

    public List<Notification> getNotificationsByAccount(String accountNumber) {
        return notificationRepository.findByRecipientAccountNumberOrderByCreatedAtDesc(accountNumber);
    }
}
