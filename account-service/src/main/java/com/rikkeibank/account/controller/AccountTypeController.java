package com.rikkeibank.account.controller;

import com.rikkeibank.common.dto.AccountTypeDto;
import com.rikkeibank.common.dto.ApiResponse;
import com.rikkeibank.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/account-types")
@RequiredArgsConstructor
public class AccountTypeController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountTypeDto>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(accountService.getAllAccountTypes()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountTypeDto>> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getAccountTypeById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccountTypeDto>> create(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody AccountTypeDto dto) {
        if (role != null && !role.contains("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "Access denied: ADMIN role required"));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account type created", accountService.createAccountType(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountTypeDto>> update(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("id") Long id,
            @Valid @RequestBody AccountTypeDto dto) {
        if (role != null && !role.contains("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "Access denied: ADMIN role required"));
        }
        return ResponseEntity.ok(ApiResponse.success("Account type updated", accountService.updateAccountType(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("id") Long id) {
        if (role != null && !role.contains("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "Access denied: ADMIN role required"));
        }
        accountService.deleteAccountType(id);
        return ResponseEntity.ok(ApiResponse.success("Account type deleted", null));
    }
}
