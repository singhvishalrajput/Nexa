package com.nexa.api.beans;


import java.math.BigDecimal;
import java.util.List;

/** Result and resolved scope travel together so the renderer cannot describe another period. */
public record TransactionQueryResult(TransactionQuery query, List<TransactionResponse> transactions, BigDecimal total) {
  public TransactionQueryResult { transactions = List.copyOf(transactions); }
}
