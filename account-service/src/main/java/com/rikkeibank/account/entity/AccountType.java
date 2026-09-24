package com.rikkeibank.account.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "account_types")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountType implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 30)
    private String typeCode; // SAVINGS, CHECKING, LOAN, VIP

    @Column(nullable = false, length = 100)
    private String typeName;

    @Column(precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal minBalance;

    private String description;
}
