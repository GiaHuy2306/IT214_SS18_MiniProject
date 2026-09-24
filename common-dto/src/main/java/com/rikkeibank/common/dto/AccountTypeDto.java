package com.rikkeibank.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountTypeDto implements Serializable {
    private Long id;

    @NotBlank(message = "Type code is required (e.g. SAVINGS, CHECKING)")
    private String typeCode;

    @NotBlank(message = "Type name is required")
    private String typeName;

    private BigDecimal interestRate;

    @NotNull(message = "Minimum balance is required")
    private BigDecimal minBalance;

    private String description;
}
