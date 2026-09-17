package com.nexa.api.repository;

import com.nexa.api.beans.BankingContent;
import com.nexa.api.beans.BankingModels.*;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** Compatibility DTOs projected from relational account and transaction fields. */
@Repository
public class JdbcBankingProductRepository implements BankingProductRepository {
  private final JdbcTemplate db;

  public JdbcBankingProductRepository(JdbcTemplate db, ObjectMapper ignored) {
    this.db = db;
  }

  public <T> List<T> list(
      String owner, Kind kind, Class<T> type, String status, int page, int size) {
    return query(owner, kind, type, null, status, page, size);
  }

  public <T> Optional<T> find(String owner, Kind kind, String id, Class<T> type) {
    return query(owner, kind, type, id, null, 0, 1).stream().findFirst();
  }

  private <T> List<T> query(
      String owner, Kind kind, Class<T> type, String id, String status, int page, int size) {
    boolean account = kind == Kind.LOAN || kind == Kind.CARD;
    String sql =
        account
            ? "SELECT p.*,p.product_id public_id FROM accounts p JOIN customers c ON"
                  + " c.id=p.customer_id WHERE c.user_id=? AND p.account_type=? AND (? IS NULL OR"
                  + " p.product_id=?) AND (? IS NULL OR p.product_status=?) ORDER BY p.product_id"
                  + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"
            : "SELECT p.*,a.account_name,a.account_number FROM transactions p JOIN accounts a ON"
                  + " a.id=p.source_account_id JOIN customers c ON c.id=a.customer_id WHERE"
                  + " p.user_id=? AND c.user_id=p.user_id AND p.record_kind=? AND (? IS NULL OR"
                  + " p.id=?) AND (? IS NULL OR p.status=?) ORDER BY p.id OFFSET ? ROWS FETCH NEXT"
                  + " ? ROWS ONLY";
    return db.query(
        sql,
        (r, n) -> type.cast(project(r, kind)),
        owner,
        kind.name(),
        id,
        id,
        status,
        status,
        (long) page * size,
        size);
  }

  private Object project(ResultSet r, Kind kind) throws SQLException {
    String id = r.getString(kind == Kind.LOAN || kind == Kind.CARD ? "public_id" : "id");
    if (kind == Kind.LOAN)
      return new Loan(
          id,
          r.getString("account_name"),
          r.getString("number_masked"),
          r.getString("product_type"),
          r.getString("balance"),
          r.getString("periodic_payment"),
          r.getString("currency_code"),
          r.getString("due_at"),
          r.getString("interest_rate"),
          r.getString("product_status"),
          r.getString("funding_account_id"),
          payments(id));
    if (kind == Kind.CARD)
      return new Card(
          id,
          r.getString("account_name"),
          r.getString("number_masked"),
          r.getString("product_type"),
          r.getString("balance"),
          r.getBigDecimal("credit_limit").subtract(r.getBigDecimal("balance")).toPlainString(),
          r.getString("credit_limit"),
          r.getString("minimum_payment"),
          r.getString("currency_code"),
          r.getString("due_at"),
          r.getString("product_status"),
          r.getString("funding_account_id"),
          cardTransactions(id));
    if (kind == Kind.MANDATE)
      return new Mandate(
          id,
          r.getString("display_name"),
          r.getString("status"),
          r.getString("amount"),
          r.getString("currency_code"),
          r.getString("frequency"),
          r.getString("effective_date"),
          r.getString("end_date"),
          r.getString("due_at"),
          r.getString("source_account_id"),
          r.getString("account_name"),
          r.getString("account_number"),
          r.getString("transaction_reference"));
    if (kind == Kind.BILL)
      return new Bill(
          id,
          r.getString("display_name"),
          r.getString("amount"),
          r.getString("minimum_amount"),
          r.getString("currency_code"),
          r.getString("due_at"),
          r.getString("status"),
          r.getString("source_account_id"),
          r.getString("transaction_reference"),
          r.getString("category"),
          r.getString("destination_masked"),
          payments(id));
    return payment(r);
  }

  private Payment payment(ResultSet r) throws SQLException {
    return new Payment(
        r.getString("id"),
        r.getString("display_name"),
        r.getString("amount"),
        r.getString("currency_code"),
        r.getString("due_at"),
        r.getString("status"),
        r.getString("source_account_id"),
        r.getString("transaction_reference"));
  }

  private List<Payment> payments(String id) {
    return db.query(
        "SELECT * FROM transactions WHERE target_id=? AND (record_kind='PRODUCT_HISTORY' OR"
            + " (record_kind='PAYMENT' AND operation='LOAN_REPAYMENT')) ORDER BY created_at DESC",
        (r, n) -> payment(r),
        id);
  }

  private List<BankingContent.Transaction> cardTransactions(String id) {
    return db.query(
        "SELECT * FROM transactions WHERE target_id=? AND record_kind='PRODUCT_HISTORY' ORDER BY"
            + " created_at DESC",
        (r, n) ->
            new BankingContent.Transaction(
                r.getString("id"),
                r.getString("source_account_id"),
                r.getString("transaction_reference"),
                r.getString("operation"),
                r.getString("merchant_name"),
                r.getString("category"),
                r.getString("amount"),
                r.getString("currency_code"),
                r.getString("status"),
                r.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC),
                r.getString("payment_method")),
        id);
  }
}
