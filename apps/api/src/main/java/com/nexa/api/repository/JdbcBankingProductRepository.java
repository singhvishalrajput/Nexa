package com.nexa.api.repository;


import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcBankingProductRepository implements BankingProductRepository {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public JdbcBankingProductRepository(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  public <T> List<T> list(
      String userId, Kind kind, Class<T> type, String status, int page, int size) {
    return db.query(
        "SELECT payload FROM banking_products p WHERE user_id = ? AND EXISTS (SELECT 1 FROM accounts a JOIN customers c ON c.id=a.customer_id WHERE a.id=p.account_id AND c.user_id=p.user_id) AND kind = ? AND (? IS NULL OR"
            + " JSON_VALUE(payload, '$.status') = ?) ORDER BY id OFFSET ? ROWS FETCH NEXT ? ROWS"
            + " ONLY",
        (row, n) -> decode(row.getString(1), type),
        userId,
        kind.name(),
        status,
        status,
        (long) page * size,
        size);
  }

  public <T> Optional<T> find(String userId, Kind kind, String id, Class<T> type) {
    return db
        .query(
            "SELECT payload FROM banking_products p WHERE user_id = ? AND EXISTS (SELECT 1 FROM accounts a JOIN customers c ON c.id=a.customer_id WHERE a.id=p.account_id AND c.user_id=p.user_id) AND kind = ? AND id = ?",
            (row, n) -> decode(row.getString(1), type),
            userId,
            kind.name(),
            id)
        .stream()
        .findFirst();
  }

  private <T> T decode(String payload, Class<T> type) {
    try {
      return json.readValue(payload, type);
    } catch (tools.jackson.core.JacksonException exception) {
      throw new IllegalStateException("Banking data is unavailable.");
    }
  }
}
