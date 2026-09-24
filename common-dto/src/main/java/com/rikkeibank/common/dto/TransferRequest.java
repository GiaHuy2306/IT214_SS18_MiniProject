package com.rikkeibank.common.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {
    @NotBlank(message = "Source account number is required")
    private String sourceAccountNumber;

    @NotBlank(message = "Target account number is required")
    private String targetAccountNumber;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1000", message = "Transfer amount must be at least 1,000 VND")
    private BigDecimal amount;

    private String description;

    // Simulation flag for testing Saga rollback / compensating transaction:
    // If set to true, destination credit will fail to prove compensation works!
    private boolean simulateFailureAtTarget;
}
