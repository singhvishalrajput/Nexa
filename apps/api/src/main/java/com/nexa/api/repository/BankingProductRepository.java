package com.nexa.api.repository;

import java.util.List;
import java.util.Optional;

/** Stable product DTO boundary over the six-table relational banking model. */
public interface BankingProductRepository {
  enum Kind {
    MANDATE,
    BILL,
    CARD,
    SCHEDULED_PAYMENT,
    LOAN
  }

  <T> List<T> list(String userId, Kind kind, Class<T> type, String status, int page, int size);

  <T> Optional<T> find(String userId, Kind kind, String id, Class<T> type);
}
