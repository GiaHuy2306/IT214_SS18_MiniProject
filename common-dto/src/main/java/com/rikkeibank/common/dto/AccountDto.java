package com.rikkeibank.common.dto;

import com.rikkeibank.common.enums.AccountStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class AccountDto implements Serializable {
    private Long id;
    private String accountNumber;

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @NotNull(message = "Account Type ID is required")
    private Long accountTypeId;

    private String accountTypeCode;
    private String accountTypeName;

    private BigDecimal balance;

    @Builder.Default
    private String currency = "VND";

    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
