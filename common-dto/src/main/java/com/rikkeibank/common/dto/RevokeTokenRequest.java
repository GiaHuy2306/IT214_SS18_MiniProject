package com.rikkeibank.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevokeTokenRequest {
    @NotBlank(message = "Target token or username is required")
    private String token;
    private String username;
    private String reason;
}
