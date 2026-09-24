package com.rikkeibank.common.event;

import com.rikkeibank.common.enums.TransactionStatus;
import com.rikkeibank.common.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent implements Serializable {
    private String eventId;
    private String eventType; // TRANSFER_COMPLETED, TRANSFER_FAILED_COMPENSATED
    private Long transactionId;
    private String transactionReference;
    private String sourceAccountNumber;
    private String targetAccountNumber;
    private BigDecimal amount;
    private String currency;
    private TransactionType type;
    private TransactionStatus status;
    private String performedBy;
    private String description;
    private String failureReason;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
