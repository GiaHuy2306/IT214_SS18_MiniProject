package com.rikkeibank.transaction.client;

import com.rikkeibank.common.dto.AccountDto;
import com.rikkeibank.common.dto.ApiResponse;
import com.rikkeibank.common.dto.BalanceUpdateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "account-service")
public interface AccountServiceClient {

    @GetMapping("/api/accounts/{accountNumber}")
    ApiResponse<AccountDto> getAccountByNumber(@PathVariable("accountNumber") String accountNumber);

    @PostMapping("/api/accounts/{accountNumber}/debit")
    ApiResponse<AccountDto> debit(
            @PathVariable("accountNumber") String accountNumber,
            @RequestBody BalanceUpdateRequest request);

    @PostMapping("/api/accounts/{accountNumber}/credit")
    ApiResponse<AccountDto> credit(
            @PathVariable("accountNumber") String accountNumber,
            @RequestBody BalanceUpdateRequest request);

    @PostMapping("/api/accounts/{accountNumber}/refund")
    ApiResponse<AccountDto> refund(
            @PathVariable("accountNumber") String accountNumber,
            @RequestBody BalanceUpdateRequest request);
}
