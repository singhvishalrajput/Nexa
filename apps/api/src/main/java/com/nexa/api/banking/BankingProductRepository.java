package com.nexa.api.banking;

import java.util.List;
import java.util.Optional;

/** Replace this read-model adapter with a bank integration without changing domain APIs. */
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
