package com.rikkeibank.account.service;

import com.rikkeibank.account.entity.Account;
import com.rikkeibank.account.entity.AccountType;
import com.rikkeibank.account.repository.AccountRepository;
import com.rikkeibank.account.repository.AccountTypeRepository;
import com.rikkeibank.common.dto.AccountDto;
import com.rikkeibank.common.dto.BalanceUpdateRequest;
import com.rikkeibank.common.enums.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountTypeRepository accountTypeRepository;

    @InjectMocks
    private AccountService accountService;

    private Account testAccount;
    private AccountType accountType;

    @BeforeEach
    void setUp() {
        accountType = AccountType.builder()
                .id(1L)
                .typeCode("CHECKING")
                .typeName("Payment Account")
                .minBalance(BigDecimal.valueOf(50000))
                .build();

        testAccount = Account.builder()
                .id(1L)
                .accountNumber("1001000001")
                .customerId(1L)
                .accountType(accountType)
                .balance(BigDecimal.valueOf(1000000)) // 1,000,000 VND
                .status(AccountStatus.ACTIVE)
                .currency("VND")
                .build();
    }

    @Test
    @DisplayName("Should successfully debit account when balance is sufficient")
    void testDebitSuccess() {
        when(accountRepository.findByAccountNumber("1001000001")).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BalanceUpdateRequest req = BalanceUpdateRequest.builder()
                .amount(BigDecimal.valueOf(200000))
                .transactionReference("TXN001")
                .build();

        AccountDto result = accountService.debit("1001000001", req);

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(800000), result.getBalance());
        verify(accountRepository, times(1)).save(testAccount);
    }

    @Test
    @DisplayName("Should throw exception when debit amount exceeds available balance")
    void testDebitInsufficientBalance() {
        when(accountRepository.findByAccountNumber("1001000001")).thenReturn(Optional.of(testAccount));

        BalanceUpdateRequest req = BalanceUpdateRequest.builder()
                .amount(BigDecimal.valueOf(980000)) // 1,000,000 - 980,000 = 20,000 < minBalance 50,000
                .transactionReference("TXN002")
                .build();

        assertThrows(IllegalArgumentException.class, () -> accountService.debit("1001000001", req));
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should successfully credit amount to account")
    void testCreditSuccess() {
        when(accountRepository.findByAccountNumber("1001000001")).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BalanceUpdateRequest req = BalanceUpdateRequest.builder()
                .amount(BigDecimal.valueOf(500000))
                .transactionReference("TXN003")
                .build();

        AccountDto result = accountService.credit("1001000001", req);

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(1500000), result.getBalance());
        verify(accountRepository, times(1)).save(testAccount);
    }

    @Test
    @DisplayName("Should refund balance during Saga compensating rollback")
    void testRefundCompensatingTransaction() {
        when(accountRepository.findByAccountNumber("1001000001")).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BalanceUpdateRequest req = BalanceUpdateRequest.builder()
                .amount(BigDecimal.valueOf(300000))
                .transactionReference("TXN004")
                .reason("Compensating rollback")
                .build();

        AccountDto result = accountService.refund("1001000001", req);

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(1300000), result.getBalance());
        verify(accountRepository, times(1)).save(testAccount);
    }
}
