package com.rikkeibank.transaction.service;

import com.rikkeibank.common.dto.*;
import com.rikkeibank.common.enums.TransactionStatus;
import com.rikkeibank.common.enums.TransactionType;
import com.rikkeibank.common.event.TransactionEvent;
import com.rikkeibank.transaction.client.AccountServiceClient;
import com.rikkeibank.transaction.entity.SagaStepLog;
import com.rikkeibank.transaction.entity.Transaction;
import com.rikkeibank.transaction.event.TransactionEventProducer;
import com.rikkeibank.transaction.repository.SagaStepLogRepository;
import com.rikkeibank.transaction.repository.TransactionRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSagaService {

    private final AccountServiceClient accountServiceClient;
    private final TransactionRepository transactionRepository;
    private final SagaStepLogRepository sagaStepLogRepository;
    private final TransactionEventProducer eventProducer;

    /**
     * Executes Distributed Transaction using SAGA Orchestrator Pattern
     * Protected by Resilience4j Circuit Breaker against cascading failures.
     */
    @CircuitBreaker(name = "accountServiceBreaker", fallbackMethod = "transferFallback")
    public TransactionDto executeTransfer(TransferRequest request, String performedBy, Long customerId) {
        String txRef = "TXN" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        log.info("--- [SAGA START] Initiating transfer {} from {} to {} for {} VND ---",
                txRef, request.getSourceAccountNumber(), request.getTargetAccountNumber(), request.getAmount());

        if (request.getSourceAccountNumber().equals(request.getTargetAccountNumber())) {
            throw new IllegalArgumentException("Source and destination accounts cannot be the same");
        }

        // STEP 1: Persist initial transaction state as PENDING
        Transaction tx = Transaction.builder()
                .transactionReference(txRef)
                .sourceAccountNumber(request.getSourceAccountNumber())
                .targetAccountNumber(request.getTargetAccountNumber())
                .amount(request.getAmount())
                .currency("VND")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.PENDING)
                .description(request.getDescription() != null ? request.getDescription() : "Money Transfer")
                .performedBy(performedBy)
                .customerId(customerId)
                .createdAt(LocalDateTime.now())
                .build();
        tx = transactionRepository.save(tx);

        boolean sourceDebited = false;

        try {
            // STEP 2: SAGA Step 1 - DEBIT source account
            log.info("--- [SAGA STEP 1] Calling Account Service to DEBIT source account {} ---", request.getSourceAccountNumber());
            BalanceUpdateRequest debitReq = BalanceUpdateRequest.builder()
                    .amount(request.getAmount())
                    .transactionReference(txRef)
                    .reason("Transfer to " + request.getTargetAccountNumber())
                    .build();

            ApiResponse<AccountDto> debitResp = accountServiceClient.debit(request.getSourceAccountNumber(), debitReq);
            if (!debitResp.isSuccess()) {
                throw new IllegalStateException("Failed to debit source account: " + debitResp.getMessage());
            }

            sourceDebited = true;
            logSagaStep(txRef, "DEBIT_SOURCE", "SUCCESS", "Debited " + request.getAmount() + " VND from " + request.getSourceAccountNumber());

            // Check if test simulation flag for target failure is active
            if (request.isSimulateFailureAtTarget()) {
                log.warn("--- [SIMULATION TRIGGERED] Simulating network/business failure at target account credit ---");
                throw new RuntimeException("Simulated target account credit failure for Saga Rollback testing");
            }

            // STEP 3: SAGA Step 2 - CREDIT target account
            log.info("--- [SAGA STEP 2] Calling Account Service to CREDIT target account {} ---", request.getTargetAccountNumber());
            BalanceUpdateRequest creditReq = BalanceUpdateRequest.builder()
                    .amount(request.getAmount())
                    .transactionReference(txRef)
                    .reason("Transfer from " + request.getSourceAccountNumber())
                    .build();

            ApiResponse<AccountDto> creditResp = accountServiceClient.credit(request.getTargetAccountNumber(), creditReq);
            if (!creditResp.isSuccess()) {
                throw new IllegalStateException("Failed to credit destination account: " + creditResp.getMessage());
            }

            logSagaStep(txRef, "CREDIT_TARGET", "SUCCESS", "Credited " + request.getAmount() + " VND to " + request.getTargetAccountNumber());

            // STEP 4: Transfer successful - Commit transaction state
            tx.setStatus(TransactionStatus.COMPLETED);
            tx.setCompletedAt(LocalDateTime.now());
            Transaction completedTx = transactionRepository.save(tx);
            log.info("--- [SAGA COMPLETED] Transfer {} successfully finished ---", txRef);

            // Publish Event-driven message to Kafka
            eventProducer.publishEvent(TransactionEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("TRANSFER_COMPLETED")
                    .transactionId(completedTx.getId())
                    .transactionReference(txRef)
                    .sourceAccountNumber(request.getSourceAccountNumber())
                    .targetAccountNumber(request.getTargetAccountNumber())
                    .amount(request.getAmount())
                    .currency("VND")
                    .type(TransactionType.TRANSFER)
                    .status(TransactionStatus.COMPLETED)
                    .performedBy(performedBy)
                    .description(request.getDescription())
                    .timestamp(LocalDateTime.now())
                    .build());

            return mapToDto(completedTx);

        } catch (Exception ex) {
            log.error("--- [SAGA EXCEPTION] Error during Saga execution for {}: {} ---", txRef, ex.getMessage());

            if (sourceDebited) {
                // STEP 5: SAGA COMPENSATING TRANSACTION (Rollback debit)
                log.warn("--- [SAGA COMPENSATING ACTION] Refunding {} VND back to source account {} ---",
                        request.getAmount(), request.getSourceAccountNumber());
                try {
                    BalanceUpdateRequest refundReq = BalanceUpdateRequest.builder()
                            .amount(request.getAmount())
                            .transactionReference(txRef)
                            .reason("Compensating rollback due to target credit failure: " + ex.getMessage())
                            .build();

                    accountServiceClient.refund(request.getSourceAccountNumber(), refundReq);
                    logSagaStep(txRef, "COMPENSATE_REFUND", "COMPENSATED", "Refunded " + request.getAmount() + " VND to " + request.getSourceAccountNumber());
                    log.warn("--- [SAGA COMPENSATING ACTION SUCCESSFUL] Source account restored! ---");

                    tx.setStatus(TransactionStatus.COMPENSATED);
                    tx.setErrorMessage("Transfer failed at destination step. Compensating transaction restored source balance. Reason: " + ex.getMessage());
                } catch (Exception compEx) {
                    log.error("--- [CRITICAL SAGA ROLLBACK ERROR] Failed to compensate refund: {} ---", compEx.getMessage());
                    logSagaStep(txRef, "COMPENSATE_REFUND", "FAILED", "Compensation error: " + compEx.getMessage());
                    tx.setStatus(TransactionStatus.FAILED);
                    tx.setErrorMessage("Transfer failed and compensation error: " + compEx.getMessage());
                }
            } else {
                tx.setStatus(TransactionStatus.FAILED);
                tx.setErrorMessage(ex.getMessage());
            }

            tx.setCompletedAt(LocalDateTime.now());
            Transaction saved = transactionRepository.save(tx);

            // Publish Failure/Compensated Event to Kafka
            eventProducer.publishEvent(TransactionEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("TRANSFER_FAILED_COMPENSATED")
                    .transactionId(saved.getId())
                    .transactionReference(txRef)
                    .sourceAccountNumber(request.getSourceAccountNumber())
                    .targetAccountNumber(request.getTargetAccountNumber())
                    .amount(request.getAmount())
                    .currency("VND")
                    .type(TransactionType.TRANSFER)
                    .status(saved.getStatus())
                    .performedBy(performedBy)
                    .description(request.getDescription())
                    .failureReason(saved.getErrorMessage())
                    .timestamp(LocalDateTime.now())
                    .build());

            return mapToDto(saved);
        }
    }

    /**
     * Resilience4j Circuit Breaker Fallback method
     * Called when account-service is down or Circuit is in OPEN state.
     */
    public TransactionDto transferFallback(TransferRequest request, String performedBy, Long customerId, Throwable t) {
        log.error("--- [CIRCUIT BREAKER FALLBACK] Account service is unavailable or circuit is OPEN. Error: {} ---", t.getMessage());
        return TransactionDto.builder()
                .transactionReference("FALLBACK-" + System.currentTimeMillis())
                .sourceAccountNumber(request.getSourceAccountNumber())
                .targetAccountNumber(request.getTargetAccountNumber())
                .amount(request.getAmount())
                .currency("VND")
                .status(TransactionStatus.FAILED)
                .errorMessage("Circuit Breaker Active: Account service is currently unavailable or unstable. Request rejected safely to avoid cascading failure.")
                .performedBy(performedBy)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void logSagaStep(String txRef, String step, String status, String details) {
        sagaStepLogRepository.save(SagaStepLog.builder()
                .transactionReference(txRef)
                .stepName(step)
                .stepStatus(status)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build());
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
