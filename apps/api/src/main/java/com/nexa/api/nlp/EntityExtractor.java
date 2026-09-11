package com.nexa.api.nlp;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface EntityExtractor {
  record Entities(
      BigDecimal amount,
      String accountId,
      String targetId,
      String payee,
      LocalDate from,
      LocalDate to,
      String accountType,
      String direction,
      String search,
      String status) {}

  Entities extract(String text, Intent intent);
}
