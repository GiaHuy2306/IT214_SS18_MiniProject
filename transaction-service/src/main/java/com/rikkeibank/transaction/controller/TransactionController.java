package com.rikkeibank.transaction.controller;

import com.rikkeibank.common.dto.ApiResponse;
import com.rikkeibank.common.dto.TransactionDto;
import com.rikkeibank.common.dto.TransferRequest;
import com.rikkeibank.transaction.entity.SagaStepLog;
import com.rikkeibank.transaction.service.TransactionQueryService;
import com.rikkeibank.transaction.service.TransactionSagaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionSagaService sagaService;
    private final TransactionQueryService queryService;

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransactionDto>> transfer(
            @RequestHeader(value = "X-User-Username", required = false) String username,
            @RequestHeader(value = "X-User-Customer-Id", required = false) String customerIdHeader,
            @Valid @RequestBody TransferRequest request) {

        String performedBy = username != null ? username : "Anonymous/System";
        Long customerId = customerIdHeader != null ? Long.parseLong(customerIdHeader) : null;

        TransactionDto dto = sagaService.executeTransfer(request, performedBy, customerId);

        if ("COMPLETED".equals(dto.getStatus().name())) {
            return ResponseEntity.ok(ApiResponse.success("Transfer completed successfully", dto));
        } else if ("COMPENSATED".equals(dto.getStatus().name())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success("Transfer failed but compensating rollback refunded source account successfully", dto));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), dto.getErrorMessage()));
        }
    }

    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<List<TransactionDto>>> getDailyTransactions(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        // Enforce TELLER or ADMIN role
        if (role != null && !role.contains("TELLER") && !role.contains("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "Access denied: TELLER or ADMIN role required"));
        }

        List<TransactionDto> transactions = queryService.getDailyTransactions(date);
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    @GetMapping("/my-history")
    public ResponseEntity<ApiResponse<List<TransactionDto>>> getMyHistory(
            @RequestHeader(value = "X-User-Customer-Id", required = false) String customerIdHeader) {
        if (customerIdHeader == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Customer identity not found in request headers"));
        }
        Long customerId = Long.parseLong(customerIdHeader);
        return ResponseEntity.ok(ApiResponse.success(queryService.getCustomerTransactions(customerId)));
    }

    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<ApiResponse<List<TransactionDto>>> getByAccount(@PathVariable("accountNumber") String accountNumber) {
        return ResponseEntity.ok(ApiResponse.success(queryService.getAccountTransactions(accountNumber)));
    }

    @GetMapping("/{reference}")
    public ResponseEntity<ApiResponse<TransactionDto>> getByReference(@PathVariable("reference") String reference) {
        return ResponseEntity.ok(ApiResponse.success(queryService.getByReference(reference)));
    }

    @GetMapping("/saga-logs/{reference}")
    public ResponseEntity<ApiResponse<List<SagaStepLog>>> getSagaLogs(@PathVariable("reference") String reference) {
        return ResponseEntity.ok(ApiResponse.success(queryService.getSagaLogs(reference)));
    }
}
