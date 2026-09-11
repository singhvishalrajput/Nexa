package com.nexa.api.core.model;

import java.math.BigDecimal;

public class TransactionRequest {
  private Long sourceAccountId;
  private Long destinationAccountId;
  private BigDecimal amount;

  public Long getSourceAccountId() {
    return sourceAccountId;
  }

  public void setSourceAccountId(Long v) {
    sourceAccountId = v;
  }

  public Long getDestinationAccountId() {
    return destinationAccountId;
  }

  public void setDestinationAccountId(Long v) {
    destinationAccountId = v;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal v) {
    amount = v;
  }
}
