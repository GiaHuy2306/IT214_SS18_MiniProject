package com.rikkeibank.transaction.service;

import com.rikkeibank.common.dto.TransactionDto;
import com.rikkeibank.transaction.entity.SagaStepLog;
import com.rikkeibank.transaction.entity.Transaction;
import com.rikkeibank.transaction.repository.SagaStepLogRepository;
import com.rikkeibank.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionQueryService {

    private final TransactionRepository transactionRepository;
    private final SagaStepLogRepository sagaStepLogRepository;

    public List<TransactionDto> getAllTransactions() {
        return transactionRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public TransactionDto getByReference(String reference) {
        Transaction tx = transactionRepository.findByTransactionReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + reference));
        return mapToDto(tx);
    }

    public List<TransactionDto> getDailyTransactions(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.atTime(LocalTime.MAX);
        log.info("Fetching daily transactions between {} and {}", start, end);
        return transactionRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<TransactionDto> getCustomerTransactions(Long customerId) {
        return transactionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<TransactionDto> getAccountTransactions(String accountNumber) {
        return transactionRepository.findByAccountNumber(accountNumber).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<SagaStepLog> getSagaLogs(String reference) {
        return sagaStepLogRepository.findByTransactionReferenceOrderByTimestampAsc(reference);
    }

    private TransactionDto mapToDto(Transaction t) {
        return TransactionDto.builder()
                .id(t.getId())
                .transactionReference(t.getTransactionReference())
                .sourceAccountNumber(t.getSourceAccountNumber())
                .targetAccountNumber(t.getTargetAccountNumber())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .fee(t.getFee())
                .type(t.getType())
                .status(t.getStatus())
                .description(t.getDescription())
                .performedBy(t.getPerformedBy())
                .errorMessage(t.getErrorMessage())
                .createdAt(t.getCreatedAt())
                .completedAt(t.getCompletedAt())
                .build();
    }
}
