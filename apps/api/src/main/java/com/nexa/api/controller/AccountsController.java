package com.nexa.api.controller;

import com.nexa.api.beans.AccountBalanceResponse;
import com.nexa.api.beans.AccountResponse;
import com.nexa.api.beans.OpenAccountRequest;
import com.nexa.api.beans.PageResponse;
import com.nexa.api.beans.TransactionResponse;
import com.nexa.api.service.AccountOpeningService;
import com.nexa.api.service.AccountQueryService;
import com.nexa.api.service.TransactionQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountsController {

  private final AccountQueryService accountQueryService;
  private final TransactionQueryService transactionQueryService;
  private final AccountOpeningService accountOpeningService;

  AccountsController(
      AccountQueryService accountQueryService,
      TransactionQueryService transactionQueryService,
      AccountOpeningService accountOpeningService) {
    this.accountQueryService = accountQueryService;
    this.transactionQueryService = transactionQueryService;
    this.accountOpeningService = accountOpeningService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AccountResponse openAccount(
      @Valid @org.springframework.web.bind.annotation.RequestBody OpenAccountRequest request) {
    return accountOpeningService.open(request);
  }

  @GetMapping
  public List<AccountResponse> accounts() {
    return accountQueryService.currentAccounts();
  }

  @GetMapping("/{accountId}")
  public AccountResponse account(@PathVariable String accountId) {
    return accountQueryService.requireOwnedAccount(accountId);
  }

  @GetMapping("/{accountId}/balance")
  public AccountBalanceResponse balance(@PathVariable String accountId) {
    return accountQueryService.balance(accountId);
  }

  @GetMapping("/{accountId}/number")
  public java.util.Map<String, String> number(@PathVariable String accountId) {
    return java.util.Map.of(
        "accountNumber", accountQueryService.requireOwnedEntity(accountId).getAccountNumber());
  }

  @GetMapping("/{accountId}/transactions")
  public PageResponse<TransactionResponse> transactions(
      @PathVariable String accountId,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
    return transactionQueryService.transactions(accountId, category, from, to, page, size);
  }
}
