package com.rikkeibank.transaction.service;

import com.rikkeibank.common.dto.*;
import com.rikkeibank.common.enums.TransactionStatus;
import com.rikkeibank.transaction.client.AccountServiceClient;
import com.rikkeibank.transaction.entity.Transaction;
import com.rikkeibank.transaction.event.TransactionEventProducer;
import com.rikkeibank.transaction.repository.SagaStepLogRepository;
import com.rikkeibank.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionSagaServiceTest {

    @Mock
    private AccountServiceClient accountServiceClient;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private SagaStepLogRepository sagaStepLogRepository;

    @Mock
    private TransactionEventProducer eventProducer;

    @InjectMocks
    private TransactionSagaService sagaService;

    private TransferRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = TransferRequest.builder()
                .sourceAccountNumber("1001000001")
                .targetAccountNumber("1001000002")
                .amount(BigDecimal.valueOf(200000))
                .description("Test Transfer")
                .simulateFailureAtTarget(false)
                .build();
    }

    @Test
    @DisplayName("SAGA Happy Path: Transfer successfully debits source and credits target")
    void testTransferHappyPath() {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            if (t.getId() == null) t.setId(1L);
            return t;
        });

        AccountDto dummyDto = AccountDto.builder().build();
        when(accountServiceClient.debit(eq("1001000001"), any(BalanceUpdateRequest.class)))
                .thenReturn(ApiResponse.success(dummyDto));
        when(accountServiceClient.credit(eq("1001000002"), any(BalanceUpdateRequest.class)))
                .thenReturn(ApiResponse.success(dummyDto));

        TransactionDto result = sagaService.executeTransfer(validRequest, "teller", 1L);

        assertNotNull(result);
        assertEquals(TransactionStatus.COMPLETED, result.getStatus());
        verify(accountServiceClient, times(1)).debit(eq("1001000001"), any());
        verify(accountServiceClient, times(1)).credit(eq("1001000002"), any());
        verify(accountServiceClient, never()).refund(any(), any());
        verify(eventProducer, times(1)).publishEvent(any());
    }

    @Test
    @DisplayName("SAGA Rollback Path: Credit failure triggers Compensating Refund on source account")
    void testTransferRollbackCompensating() {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            if (t.getId() == null) t.setId(2L);
            return t;
        });

        AccountDto dummyDto = AccountDto.builder().build();
        when(accountServiceClient.debit(eq("1001000001"), any(BalanceUpdateRequest.class)))
                .thenReturn(ApiResponse.success(dummyDto));
        when(accountServiceClient.credit(eq("1001000002"), any(BalanceUpdateRequest.class)))
                .thenReturn(ApiResponse.error(400, "Destination account is locked"));

        TransactionDto result = sagaService.executeTransfer(validRequest, "teller", 1L);

        assertNotNull(result);
        assertEquals(TransactionStatus.COMPENSATED, result.getStatus());
        verify(accountServiceClient, times(1)).debit(eq("1001000001"), any());
        // Verify compensating refund was invoked!
        verify(accountServiceClient, times(1)).refund(eq("1001000001"), any());
        verify(eventProducer, times(1)).publishEvent(any());
    }

    @Test
    @DisplayName("Circuit Breaker Fallback: Returns safe fallback response when service is down")
    void testCircuitBreakerFallback() {
        Throwable cause = new RuntimeException("Connection refused");
        TransactionDto fallback = sagaService.transferFallback(validRequest, "teller", 1L, cause);

        assertNotNull(fallback);
        assertEquals(TransactionStatus.FAILED, fallback.getStatus());
        assertTrue(fallback.getErrorMessage().contains("Circuit Breaker Active"));
    }
}
