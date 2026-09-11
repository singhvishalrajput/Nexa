package com.nexa.api.transactions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionResponse(
    String id,
    String accountId,
    String reference,
    String type,
    String merchantName,
    String category,
    BigDecimal amount,
    String currencyCode,
    String status,
    OffsetDateTime occurredAt,
    String paymentMethod) {
  public TransactionResponse(
      String id,
      String accountId,
      String reference,
      String type,
      String merchantName,
      String category,
      BigDecimal amount,
      String currencyCode,
      String status,
      OffsetDateTime occurredAt) {
    this(
        id,
        accountId,
        reference,
        type,
        merchantName,
        category,
        amount,
        currencyCode,
        status,
        occurredAt,
        null);
  }

  @com.fasterxml.jackson.annotation.JsonProperty("direction")
  public String direction() {
    return amount.signum() > 0 ? "CREDIT" : amount.signum() < 0 ? "DEBIT" : "NONE";
  }
}
