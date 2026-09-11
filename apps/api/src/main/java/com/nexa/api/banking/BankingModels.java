package com.nexa.api.banking;

import com.nexa.api.conversations.BankingContent;
import java.util.List;

/** Display-safe read models. Decimal text preserves money across JSON clients. */
public final class BankingModels {
  private BankingModels() {}

  public static String mask(String value) {
    if (value == null) return null;
    String digits = value.replaceAll("[^0-9]", "");
    return digits.isEmpty()
        ? "Number unavailable"
        : "•••• " + digits.substring(Math.max(0, digits.length() - 4));
  }

  public record Mandate(
      String id,
      String payee,
      String status,
      String limit,
      String currencyCode,
      String frequency,
      String startDate,
      String endDate,
      String nextDebit,
      String accountId,
      String accountName,
      String accountNumberMasked,
      String reference) {
    public Mandate {
      accountNumberMasked = mask(accountNumberMasked);
    }
  }

  public record Payment(
      String id,
      String payee,
      String amount,
      String currencyCode,
      String dueAt,
      String status,
      String accountId,
      String reference) {}

  public record Bill(
      String id,
      String billerName,
      String amount,
      String minimumAmount,
      String currencyCode,
      String dueAt,
      String status,
      String accountId,
      String reference,
      String category,
      String customerNumberMasked,
      List<Payment> paymentHistory) {
    public Bill {
      customerNumberMasked = mask(customerNumberMasked);
    }
  }

  public record Card(
      String id,
      String displayName,
      String numberMasked,
      String cardType,
      String outstanding,
      String availableLimit,
      String creditLimit,
      String minimumPayment,
      String currencyCode,
      String dueAt,
      String status,
      String accountId,
      List<BankingContent.Transaction> transactions) {
    public Card {
      numberMasked = mask(numberMasked);
    }
  }

  public record Beneficiary(
      String id, String displayName, String bankName, String accountNumberMasked, String status) {
    public Beneficiary {
      accountNumberMasked = mask(accountNumberMasked);
    }
  }

  public record Loan(
      String id,
      String displayName,
      String numberMasked,
      String loanType,
      String outstanding,
      String nextEmi,
      String currencyCode,
      String dueAt,
      String interestRate,
      String status,
      String accountId,
      List<Payment> paymentHistory) {
    public Loan {
      numberMasked = mask(numberMasked);
    }
  }

  public record Transfer(
      String id,
      String sourceAccountId,
      String beneficiaryId,
      String reference,
      String amount,
      String currencyCode,
      String status) {}

  public record PreparedAction(
      String operation,
      String status,
      String accountId,
      String targetId,
      String amount,
      String currencyCode,
      boolean confirmationRequired,
      boolean executionAvailable) {}
}
