package com.nexa.api.service;


import com.nexa.api.service.CurrentUserProvider;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aggregates the authoritative ledger-facing transaction records, never a page of chat results. */
@Service
@Transactional(readOnly = true)
public class SpendingQueryService {
  public record Category(String category, String currency, String amount) {}

  private final JdbcTemplate db;
  private final CurrentUserProvider user;

  public SpendingQueryService(JdbcTemplate db, CurrentUserProvider user) {
    this.db = db;
    this.user = user;
  }

  public List<Category> spending(LocalDate from, LocalDate until, String category) {
    return db.query(
        """
        SELECT COALESCE(t.category, 'Uncategorised'), a.currency_code, SUM(t.amount)
        FROM transactions t JOIN accounts a ON a.id=t.source_account_id
        JOIN customers c ON c.id=a.customer_id
        LEFT JOIN accounts dest ON dest.id=t.destination_account_id
        LEFT JOIN customers recipient ON recipient.id=dest.customer_id
        WHERE c.user_id=? AND t.status='SUCCESS' AND t.created_at>=? AND t.created_at<?
          AND (recipient.user_id IS NULL OR recipient.user_id<>?)
          AND (? IS NULL OR LOWER(t.category)=?)
        GROUP BY COALESCE(t.category, 'Uncategorised'), a.currency_code
        ORDER BY SUM(t.amount) DESC
        """,
        (r, n) -> new Category(r.getString(1), r.getString(2), r.getBigDecimal(3).toPlainString()),
        user.userId(),
        java.sql.Timestamp.valueOf(from.atStartOfDay()),
        java.sql.Timestamp.valueOf(until.atStartOfDay()),
        user.userId(),
        category,
        category);
  }
}
