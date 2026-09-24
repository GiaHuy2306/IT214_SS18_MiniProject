package com.rikkeibank.account.service;

import com.rikkeibank.account.entity.Account;
import com.rikkeibank.account.entity.AccountType;
import com.rikkeibank.account.repository.AccountRepository;
import com.rikkeibank.account.repository.AccountTypeRepository;
import com.rikkeibank.common.dto.AccountDto;
import com.rikkeibank.common.dto.AccountTypeDto;
import com.rikkeibank.common.dto.BalanceUpdateRequest;
import com.rikkeibank.common.enums.AccountStatus;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountTypeRepository accountTypeRepository;

    @PostConstruct
    public void initDefaultData() {
        if (accountTypeRepository.count() == 0) {
            log.info("Initializing Account Types and Default Bank Accounts");
            AccountType savings = accountTypeRepository.save(AccountType.builder()
                    .typeCode("SAVINGS")
                    .typeName("Tiet Kiem Co Ky Han")
                    .interestRate(BigDecimal.valueOf(5.5))
                    .minBalance(BigDecimal.valueOf(50000))
                    .description("Tai khoan tiet kiem sinh loi cao")
                    .build());

            AccountType checking = accountTypeRepository.save(AccountType.builder()
                    .typeCode("CHECKING")
                    .typeName("Tai Khoan Thanh Toan")
                    .interestRate(BigDecimal.valueOf(0.2))
                    .minBalance(BigDecimal.valueOf(50000))
                    .description("Tai khoan thanh toan hang ngay")
                    .build());

            accountTypeRepository.save(AccountType.builder()
                    .typeCode("VIP")
                    .typeName("Tai Khoan Khach Hang VIP")
                    .interestRate(BigDecimal.valueOf(6.5))
                    .minBalance(BigDecimal.valueOf(1000000))
                    .description("Tai khoan dac quyen danh cho hoi vien VIP")
                    .build());

            // Initialize default accounts for customers
            if (accountRepository.count() == 0) {
                accountRepository.save(Account.builder()
                        .accountNumber("1001000001")
                        .customerId(1L)
                        .accountType(checking)
                        .balance(BigDecimal.valueOf(10000000)) // 10,000,000 VND
                        .currency("VND")
                        .status(AccountStatus.ACTIVE)
                        .build());

                accountRepository.save(Account.builder()
                        .accountNumber("1001000002")
                        .customerId(2L)
                        .accountType(checking)
                        .balance(BigDecimal.valueOf(5000000)) // 5,000,000 VND
                        .currency("VND")
                        .status(AccountStatus.ACTIVE)
                        .build());
            }
        }
    }

    // --- Account Type Operations (Catalog) ---
    public List<AccountTypeDto> getAllAccountTypes() {
        return accountTypeRepository.findAll().stream()
                .map(this::mapTypeToDto)
                .collect(Collectors.toList());
    }

    public AccountTypeDto getAccountTypeById(Long id) {
        AccountType type = accountTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account type not found with ID: " + id));
        return mapTypeToDto(type);
    }

    @Transactional
    public AccountTypeDto createAccountType(AccountTypeDto dto) {
        if (accountTypeRepository.existsByTypeCode(dto.getTypeCode())) {
            throw new IllegalArgumentException("Account type code already exists: " + dto.getTypeCode());
        }
        AccountType type = AccountType.builder()
                .typeCode(dto.getTypeCode().toUpperCase())
                .typeName(dto.getTypeName())
                .interestRate(dto.getInterestRate())
                .minBalance(dto.getMinBalance())
                .description(dto.getDescription())
                .build();
        return mapTypeToDto(accountTypeRepository.save(type));
    }

    @Transactional
    public AccountTypeDto updateAccountType(Long id, AccountTypeDto dto) {
        AccountType type = accountTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account type not found with ID: " + id));
        type.setTypeName(dto.getTypeName());
        type.setInterestRate(dto.getInterestRate());
        type.setMinBalance(dto.getMinBalance());
        type.setDescription(dto.getDescription());
        return mapTypeToDto(accountTypeRepository.save(type));
    }

    @Transactional
    public void deleteAccountType(Long id) {
        if (!accountTypeRepository.existsById(id)) {
            throw new IllegalArgumentException("Account type not found with ID: " + id);
        }
        accountTypeRepository.deleteById(id);
    }

    // --- Bank Account Operations ---
    public List<AccountDto> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::mapAccountToDto)
                .collect(Collectors.toList());
    }

    public AccountDto getAccountByNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));
        return mapAccountToDto(account);
    }

    public List<AccountDto> getAccountsByCustomerId(Long customerId) {
        return accountRepository.findByCustomerId(customerId).stream()
                .map(this::mapAccountToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public AccountDto createAccount(AccountDto dto) {
        AccountType type = accountTypeRepository.findById(dto.getAccountTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid account type ID: " + dto.getAccountTypeId()));

        String accNumber = dto.getAccountNumber() != null && !dto.getAccountNumber().isBlank()
                ? dto.getAccountNumber()
                : generateAccountNumber();

        if (accountRepository.existsByAccountNumber(accNumber)) {
            throw new IllegalArgumentException("Account number already exists: " + accNumber);
        }

        BigDecimal initialBalance = dto.getBalance() != null ? dto.getBalance() : type.getMinBalance();
        if (initialBalance.compareTo(type.getMinBalance()) < 0) {
            throw new IllegalArgumentException("Initial balance cannot be less than minimum balance: " + type.getMinBalance());
        }

        Account account = Account.builder()
                .accountNumber(accNumber)
                .customerId(dto.getCustomerId())
                .accountType(type)
                .balance(initialBalance)
                .currency(dto.getCurrency() != null ? dto.getCurrency() : "VND")
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return mapAccountToDto(accountRepository.save(account));
    }

    // --- Saga & Core Balance Operations ---
    @Transactional
    public AccountDto debit(String accountNumber, BalanceUpdateRequest request) {
        log.info("Processing DEBIT on account {}: amount = {}", accountNumber, request.getAmount());
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active (Status: " + account.getStatus() + ")");
        }

        BigDecimal minBal = account.getAccountType().getMinBalance();
        BigDecimal availableBalance = account.getBalance().subtract(minBal);
        if (request.getAmount().compareTo(availableBalance) > 0) {
            throw new IllegalArgumentException(String.format(
                    "Insufficient balance. Current balance: %s, Min required: %s, Available to transfer: %s, Requested: %s",
                    account.getBalance(), minBal, availableBalance, request.getAmount()));
        }

        account.setBalance(account.getBalance().subtract(request.getAmount()));
        account.setUpdatedAt(LocalDateTime.now());
        Account saved = accountRepository.save(account);
        log.info("DEBIT successful for {}. New balance: {}", accountNumber, saved.getBalance());
        return mapAccountToDto(saved);
    }

    @Transactional
    public AccountDto credit(String accountNumber, BalanceUpdateRequest request) {
        log.info("Processing CREDIT on account {}: amount = {}", accountNumber, request.getAmount());
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active (Status: " + account.getStatus() + ")");
        }

        account.setBalance(account.getBalance().add(request.getAmount()));
        account.setUpdatedAt(LocalDateTime.now());
        Account saved = accountRepository.save(account);
        log.info("CREDIT successful for {}. New balance: {}", accountNumber, saved.getBalance());
        return mapAccountToDto(saved);
    }

    @Transactional
    public AccountDto refund(String accountNumber, BalanceUpdateRequest request) {
        log.warn("--- [SAGA COMPENSATION] Processing REFUND rollback on account {}: amount = {}, reason: {} ---",
                accountNumber, request.getAmount(), request.getReason());
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));

        account.setBalance(account.getBalance().add(request.getAmount()));
        account.setUpdatedAt(LocalDateTime.now());
        Account saved = accountRepository.save(account);
        log.warn("--- [SAGA COMPENSATION SUCCESS] Restored balance for {}. Balance: {} ---", accountNumber, saved.getBalance());
        return mapAccountToDto(saved);
    }

    @Transactional
    public AccountDto updateStatus(String accountNumber, AccountStatus status) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountNumber));
        account.setStatus(status);
        account.setUpdatedAt(LocalDateTime.now());
        return mapAccountToDto(accountRepository.save(account));
    }

    private String generateAccountNumber() {
        return "100" + (1000000 + new Random().nextInt(9000000));
    }

    private AccountTypeDto mapTypeToDto(AccountType t) {
        return AccountTypeDto.builder()
                .id(t.getId())
                .typeCode(t.getTypeCode())
                .typeName(t.getTypeName())
                .interestRate(t.getInterestRate())
                .minBalance(t.getMinBalance())
                .description(t.getDescription())
                .build();
    }

    private AccountDto mapAccountToDto(Account a) {
        return AccountDto.builder()
                .id(a.getId())
                .accountNumber(a.getAccountNumber())
                .customerId(a.getCustomerId())
                .accountTypeId(a.getAccountType().getId())
                .accountTypeCode(a.getAccountType().getTypeCode())
                .accountTypeName(a.getAccountType().getTypeName())
                .balance(a.getBalance())
                .currency(a.getCurrency())
                .status(a.getStatus())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
