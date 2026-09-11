package com.nexa.api.banking;

import com.nexa.api.accounts.AccountQueryService;
import com.nexa.api.core.model.TransactionRequest;
import com.nexa.api.core.service.TransactionService;
import com.nexa.api.shared.errors.InvalidRequestException;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer capability boundary; management services alone do not enforce customer ownership. */
@Service
public class CustomerTransferService {
  private final AccountQueryService accounts;
  private final TransactionService transactions;

  public CustomerTransferService(AccountQueryService accounts, TransactionService transactions) {
    this.accounts = accounts;
    this.transactions = transactions;
  }

  public void validate(String source, String destination, BigDecimal amount) {
    var from = accounts.requireOwnedAccount(source);
    var to = accounts.requireOwnedAccount(destination);
    if (source.equals(destination))
      throw new InvalidRequestException("Choose two different accounts.");
    if (!from.status().equals("ACTIVE") || !to.status().equals("ACTIVE"))
      throw new InvalidRequestException("Both accounts must be active.");
    if (!from.currencyCode().equals(to.currencyCode()))
      throw new InvalidRequestException("Transfers require accounts in the same currency.");
    if (amount == null
        || amount.signum() <= 0
        || amount.scale() > 2
        || amount.precision() - amount.scale() > 13)
      throw new InvalidRequestException("Enter a positive amount with at most two decimal places.");
    if (amount.compareTo(from.availableBalance()) > 0)
      throw new InvalidRequestException(
          "The available balance is insufficient. Choose a smaller amount.");
  }

  @Transactional
  public String execute(String source, String destination, BigDecimal amount) {
    validate(source, destination, amount);
    var request = new TransactionRequest();
    request.setSourceAccountId(Long.valueOf(source));
    request.setDestinationAccountId(Long.valueOf(destination));
    request.setAmount(amount);
    return transactions.transfer(request).getId();
  }
}
