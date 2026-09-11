package com.nexa.api.conversations;

import com.nexa.api.accounts.AccountResponse;
import com.nexa.api.transactions.TransactionResponse;
import java.time.OffsetDateTime;
import java.util.List;

/** Versioned, display-safe snapshots. Money is decimal text, never floating point. */
@com.fasterxml.jackson.annotation.JsonInclude(
    com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record BankingContent(
    int version,
    String type,
    List<Account> accounts,
    Account account,
    List<Transaction> transactions,
    long totalElements,
    List<com.nexa.api.banking.BankingModels.Mandate> mandates,
    List<com.nexa.api.banking.BankingModels.Bill> bills,
    List<com.nexa.api.banking.BankingModels.Card> cards,
    List<com.nexa.api.banking.BankingModels.Beneficiary> beneficiaries,
    List<com.nexa.api.banking.BankingModels.Payment> payments,
    List<com.nexa.api.banking.BankingModels.Loan> loans,
    com.nexa.api.banking.BankingModels.PreparedAction action,
    com.nexa.api.banking.BankingModels.Transfer transfer,
    String responseType,
    String errorCode,
    java.util.Map<String, Object> meta) {
  public BankingContent(
      int version,
      String type,
      List<Account> accounts,
      Account account,
      List<Transaction> transactions,
      long totalElements) {
    this(
        version,
        type,
        accounts,
        account,
        transactions,
        totalElements,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public static BankingContent domain(String type, String responseType, Object data) {
    return new BankingContent(
        1,
        type,
        null,
        null,
        null,
        0,
        type.equals("MANDATES") ? cast(data) : null,
        type.equals("BILLS") ? cast(data) : null,
        type.equals("CARDS") ? cast(data) : null,
        type.equals("BENEFICIARIES") ? cast(data) : null,
        type.equals("SCHEDULED_PAYMENTS") ? cast(data) : null,
        type.equals("LOANS") ? cast(data) : null,
        type.equals("ACTION_REQUIRED")
            ? (com.nexa.api.banking.BankingModels.PreparedAction) data
            : null,
        type.equals("TRANSFER_STATUS") ? (com.nexa.api.banking.BankingModels.Transfer) data : null,
        responseType,
        null,
        null);
  }

  @SuppressWarnings("unchecked")
  private static <T> List<T> cast(Object data) {
    return (List<T>) data;
  }

  public BankingContent describe(
      String responseType, String errorCode, java.util.Map<String, Object> meta) {
    return new BankingContent(
        version,
        type,
        accounts,
        account,
        transactions,
        totalElements,
        mandates,
        bills,
        cards,
        beneficiaries,
        payments,
        loans,
        action,
        transfer,
        responseType,
        errorCode,
        meta);
  }

  public String envelopeType() {
    if (responseType != null) return responseType;
    return switch (type) {
      case "ACCOUNTS" -> "ACCOUNT_LIST";
      case "TRANSACTIONS" -> "TRANSACTION_LIST";
      default -> type;
    };
  }

  public record Account(
      String id,
      String displayName,
      String accountNumberMasked,
      String accountType,
      String currencyCode,
      String availableBalance,
      String status,
      OffsetDateTime updatedAt,
      String currentBalance) {
    public static Account from(AccountResponse value) {
      return new Account(
          value.id(),
          value.displayName(),
          value.accountNumberMasked(),
          value.accountType(),
          value.currencyCode(),
          value.availableBalance().toPlainString(),
          value.status(),
          value.updatedAt(),
          value.ledgerBalance().toPlainString());
    }
  }

  public record Transaction(
      String id,
      String accountId,
      String reference,
      String type,
      String merchantName,
      String category,
      String amount,
      String currencyCode,
      String status,
      OffsetDateTime occurredAt,
      String paymentMethod) {
    public static Transaction from(TransactionResponse value) {
      return new Transaction(
          value.id(),
          value.accountId(),
          value.reference(),
          value.type(),
          value.merchantName(),
          value.category(),
          value.amount().toPlainString(),
          value.currencyCode(),
          value.status(),
          value.occurredAt(),
          value.paymentMethod());
    }
  }

  public static BankingContent balances(List<AccountResponse> accounts) {
    return new BankingContent(
        1, "ACCOUNTS", accounts.stream().map(Account::from).toList(), null, null, 0);
  }

  public static BankingContent recent(
      AccountResponse account, List<TransactionResponse> transactions, long total) {
    return new BankingContent(
        1,
        "TRANSACTIONS",
        null,
        Account.from(account),
        transactions.stream().map(Transaction::from).toList(),
        total);
  }
}
