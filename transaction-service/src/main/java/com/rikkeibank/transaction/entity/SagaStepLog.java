package com.rikkeibank.transaction.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "saga_step_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaStepLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String transactionReference;

    @Column(nullable = false, length = 50)
    private String stepName; // DEBIT_SOURCE, CREDIT_TARGET, COMPENSATE_REFUND

    @Column(nullable = false, length = 20)
    private String stepStatus; // SUCCESS, FAILED, COMPENSATED

    @Column(length = 500)
    private String details;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
