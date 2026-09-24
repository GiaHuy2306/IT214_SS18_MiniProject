package com.rikkeibank.account.controller;

import com.rikkeibank.account.service.AccountService;
import com.rikkeibank.common.dto.AccountDto;
import com.rikkeibank.common.dto.ApiResponse;
import com.rikkeibank.common.dto.BalanceUpdateRequest;
import com.rikkeibank.common.enums.AccountStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountDto>>> getAllAccounts() {
        return ResponseEntity.ok(ApiResponse.success(accountService.getAllAccounts()));
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<ApiResponse<AccountDto>> getByAccountNumber(@PathVariable("accountNumber") String accountNumber) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getAccountByNumber(accountNumber)));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<AccountDto>>> getByCustomerId(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Customer-Id", required = false) String userCustomerId,
            @PathVariable("customerId") Long customerId) {
        // Enforce customer privacy: Customer can only view their own accounts
        if (role != null && role.contains("CUSTOMER") && userCustomerId != null) {
            if (!userCustomerId.equals(String.valueOf(customerId))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "Access denied: Customers can only view their own accounts"));
            }
        }
        return ResponseEntity.ok(ApiResponse.success(accountService.getAccountsByCustomerId(customerId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccountDto>> createAccount(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody AccountDto dto) {
        if (role != null && !role.contains("ADMIN") && !role.contains("TELLER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "Access denied: ADMIN or TELLER required to open accounts"));
        }
        AccountDto created = accountService.createAccount(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created successfully", created));
    }

    @PostMapping("/{accountNumber}/debit")
    public ResponseEntity<ApiResponse<AccountDto>> debit(
            @PathVariable("accountNumber") String accountNumber,
            @Valid @RequestBody BalanceUpdateRequest request) {
        AccountDto updated = accountService.debit(accountNumber, request);
        return ResponseEntity.ok(ApiResponse.success("Account debited successfully", updated));
    }

    @PostMapping("/{accountNumber}/credit")
    public ResponseEntity<ApiResponse<AccountDto>> credit(
            @PathVariable("accountNumber") String accountNumber,
            @Valid @RequestBody BalanceUpdateRequest request) {
        AccountDto updated = accountService.credit(accountNumber, request);
        return ResponseEntity.ok(ApiResponse.success("Account credited successfully", updated));
    }

    @PostMapping("/{accountNumber}/refund")
    public ResponseEntity<ApiResponse<AccountDto>> refund(
            @PathVariable("accountNumber") String accountNumber,
            @Valid @RequestBody BalanceUpdateRequest request) {
        AccountDto updated = accountService.refund(accountNumber, request);
        return ResponseEntity.ok(ApiResponse.success("Saga refund executed successfully", updated));
    }

    @PutMapping("/{accountNumber}/status")
    public ResponseEntity<ApiResponse<AccountDto>> updateStatus(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("accountNumber") String accountNumber,
            @RequestParam("status") AccountStatus status) {
        if (role != null && !role.contains("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "Access denied: ADMIN required"));
        }
        AccountDto updated = accountService.updateStatus(accountNumber, status);
        return ResponseEntity.ok(ApiResponse.success("Account status updated", updated));
    }
}
