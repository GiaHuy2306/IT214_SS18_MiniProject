package com.rikkeibank.notification.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String recipientAccountNumber;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(length = 50)
    private String transactionReference;

    @Column(length = 30)
    private String type; // DEBIT_ALERT, CREDIT_ALERT, ROLLBACK_ALERT

    @Builder.Default
    private boolean readStatus = false;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
