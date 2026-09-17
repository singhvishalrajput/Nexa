package com.nexa.api.service;

import com.nexa.api.beans.Account;
import com.nexa.api.beans.AccountBalanceResponse;
import com.nexa.api.beans.AccountResponse;
import com.nexa.api.exep.ResourceNotFoundException;
import com.nexa.api.repository.AccountDao;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountQueryService {
  private final CurrentUserProvider user;
  private final AccountDao accounts;

  public AccountQueryService(CurrentUserProvider user, AccountDao accounts) {
    this.user = user;
    this.accounts = accounts;
  }

  public List<AccountResponse> currentAccounts() {
    return accounts.findByCustomerUserIdOrderByCreatedAtAsc(user.userId()).stream()
        .filter(
            a ->
                a.getAccountType() == com.nexa.api.beans.AccountType.SAVINGS
                    || a.getAccountType() == com.nexa.api.beans.AccountType.CURRENT)
        .map(AccountQueryService::toResponse)
        .toList();
  }

  public AccountResponse requireOwnedAccount(String id) {
    return toResponse(requireOwnedEntity(id));
  }

  public Account requireOwnedEntity(String id) {
    try {
      return accounts
          .findByIdAndCustomerUserId(Long.valueOf(id), user.userId())
          .orElseThrow(() -> new ResourceNotFoundException("The account was not found."));
    } catch (NumberFormatException e) {
      throw new ResourceNotFoundException("The account was not found.");
    }
  }

  public AccountBalanceResponse balance(String id) {
    var a = requireOwnedAccount(id);
    return new AccountBalanceResponse(
        a.id(), a.currencyCode(), a.availableBalance(), a.ledgerBalance(), a.updatedAt());
  }

  public static AccountResponse toResponse(Account a) {
    String n = a.getAccountNumber();
    return new AccountResponse(
        a.getId().toString(),
        a.getAccountName(),
        "•••• " + n.substring(Math.max(0, n.length() - 4)),
        a.getAccountType().name(),
        a.getCurrencyCode(),
        a.getBalance(),
        a.getBalance(),
        a.getStatus().name(),
        a.getUpdatedAt() == null ? null : a.getUpdatedAt().atOffset(ZoneOffset.UTC));
  }
}
