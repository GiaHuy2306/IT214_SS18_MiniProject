package com.rikkeibank.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDto implements Serializable {
    private Long id;
    private String cifCode;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Identity number is required")
    private String identityNumber;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    private String address;
    private LocalDate dateOfBirth;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
