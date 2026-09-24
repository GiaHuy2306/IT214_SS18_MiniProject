package com.rikkeibank.common.dto;

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
public class TransactionDto implements Serializable {
    private Long id;
    private String transactionReference;
    private String sourceAccountNumber;
    private String targetAccountNumber;
    private BigDecimal amount;
    private String currency;
    private BigDecimal fee;
    private TransactionType type;
    private TransactionStatus status;
    private String description;
    private String performedBy;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
