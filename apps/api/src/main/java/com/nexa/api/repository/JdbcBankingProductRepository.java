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
  private final java.time.Clock clock;
  private final com.nexa.api.service.BusinessDateResolver dates;

  public JdbcBankingProductRepository(
      JdbcTemplate db,
      ObjectMapper ignored,
      java.time.Clock clock,
      com.nexa.api.service.BusinessDateResolver dates) {
    this.db = db;
    this.clock = clock;
    this.dates = dates;
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
    if (kind == Kind.BILL) return bills(owner, type, id, status, page, size);
    boolean account = kind == Kind.LOAN || kind == Kind.CARD;
    String state =
        kind == Kind.LOAN
            ? "CASE WHEN p.tenure_months IS NOT NULL AND p.product_status='ACTIVE' AND p.due_at<'"
                + java.time.LocalDate.now(clock.withZone(dates.zone()))
                + "' THEN 'OVERDUE' ELSE p.product_status END"
            : kind == Kind.CARD
                ? "CASE WHEN p.product_status IN ('PENDING_APPROVAL','REJECTED') THEN p.product_status"
                    + " WHEN p.status<>'ACTIVE' THEN p.status ELSE p.product_status END"
                : "p.product_status";
    String sql =
        account
            ? "SELECT p.*,p.product_id public_id,"
                + state
                + " display_status FROM accounts p JOIN customers c ON"
                + " c.id=p.customer_id WHERE c.user_id=? AND p.account_type=? AND (? IS NULL OR"
                + " p.product_id=?) AND (? IS NULL OR "
                + state
                + "=?) ORDER BY p.product_id"
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

  private <T> List<T> bills(String owner, Class<T> type, String id, String status, int page, int size) {
    String today = java.time.LocalDate.now(clock.withZone(dates.zone())).toString();
    // Derive status before applying the filter and pagination, using the same posting evidence as settlement.
    String sql = "SELECT b.* FROM (SELECT p.*,COALESCE(posted.paid_amount,0) paid_amount,"
        + " CASE WHEN p.status='PAID' OR COALESCE(posted.paid_amount,0)>=p.amount THEN 'PAID'"
        + " WHEN SUBSTR(p.due_at,1,10)<? THEN 'OVERDUE' WHEN SUBSTR(p.due_at,1,10)=? THEN 'DUE'"
        + " ELSE 'UPCOMING' END display_status,"
        + " CASE WHEN p.status='PAID' THEN 0 ELSE GREATEST(p.amount-COALESCE(posted.paid_amount,0),0) END outstanding_amount,"
        + " recipient.full_name recipient_full_name,destination.account_number recipient_account_number"
        + " FROM transactions p JOIN customers c ON c.user_id=p.user_id"
        + " LEFT JOIN accounts a ON a.id=p.source_account_id"
        + " LEFT JOIN accounts destination ON destination.id=p.destination_account_id"
        + " LEFT JOIN customers recipient ON recipient.id=destination.customer_id"
        + " LEFT JOIN (SELECT t.target_id,SUM(t.amount) paid_amount FROM transactions t"
        + " WHERE t.record_kind='PAYMENT' AND t.operation='BILL_PAYMENT' AND t.status='SUCCESS'"
        + " AND EXISTS (SELECT 1 FROM journal_entries j WHERE j.transaction_id=t.id AND j.status='POSTED')"
        + " GROUP BY t.target_id) posted ON posted.target_id=p.id"
        + " WHERE p.user_id=? AND p.record_kind='BILL' AND (p.source_account_id IS NULL OR a.customer_id=c.id)"
        + " AND (? IS NULL OR p.id=?)) b WHERE (? IS NULL OR b.display_status=?)"
        + " ORDER BY b.id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
    return db.query(sql, (r, n) -> type.cast(project(r, Kind.BILL)),
        today, today, owner, id, id, status, status, (long) page * size, size);
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
          nextEmi(id, r),
          r.getString("currency_code"),
          r.getString("due_at"),
          r.getString("interest_rate"),
          r.getString("display_status"),
          r.getString("funding_account_id"),
          payments(id),
          new com.nexa.api.beans.LoanModels.Terms(
              r.getString("application_key"),
              r.getString("loan_purpose"),
              r.getBigDecimal("principal_amount"),
              r.getBigDecimal("interest_rate"),
              r.getObject("tenure_months") == null ? null : r.getInt("tenure_months"),
              r.getBigDecimal("periodic_payment"),
              r.getTimestamp("approved_at") == null
                  ? null
                  : r.getTimestamp("approved_at").toLocalDateTime(),
              r.getTimestamp("closed_at") == null
                  ? null
                  : r.getTimestamp("closed_at").toLocalDateTime()));
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
          cardStatus(r),
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
          r.getString("display_status"),
          r.getString("source_account_id"),
          r.getString("transaction_reference"),
          r.getString("category"),
          r.getString("destination_masked"),
          billPayments(id),
          r.getBigDecimal("paid_amount").setScale(2).toPlainString(),
          r.getBigDecimal("outstanding_amount") == null ? null : r.getBigDecimal("outstanding_amount").setScale(2).toPlainString(),
          r.getString("target_id"),
          r.getString("recipient_full_name"),
          r.getString("recipient_account_number"));
    return payment(r);
  }

  private String cardStatus(ResultSet r) throws SQLException {
    String product = r.getString("product_status");
    if ("PENDING_APPROVAL".equals(product) || "REJECTED".equals(product)) return product;
    String bank = r.getString("status");
    return "ACTIVE".equals(bank) ? product : bank;
  }

  private Payment payment(ResultSet r) throws SQLException {
    return new Payment(
        r.getString("id"),
        r.getString("display_name") == null
            ? r.getString("operation")
            : r.getString("display_name"),
        r.getString("amount"),
        r.getString("currency_code"),
        r.getString("due_at") == null ? r.getString("created_at") : r.getString("due_at"),
        r.getString("status"),
        r.getString("source_account_id"),
        r.getString("transaction_reference"));
  }

  private String nextEmi(String id, ResultSet r) throws SQLException {
    if (r.getObject("tenure_months") == null) return r.getString("periodic_payment");
    if ("CLOSED".equals(r.getString("product_status"))) return null;
    var amounts =
        db.queryForList(
            "SELECT amount FROM transactions WHERE target_id=? AND record_kind='LOAN_INSTALLMENT'"
                + " AND status='PENDING' ORDER BY installment_number FETCH FIRST 1 ROW ONLY",
            java.math.BigDecimal.class,
            id);
    return amounts.isEmpty() ? r.getString("periodic_payment") : amounts.get(0).toPlainString();
  }

  private List<Payment> payments(String id) {
    return db.query(
        "SELECT * FROM transactions WHERE target_id=? AND (record_kind='PRODUCT_HISTORY' OR"
            + " (record_kind='PAYMENT' AND operation='LOAN_REPAYMENT')) ORDER BY created_at DESC",
        (r, n) -> payment(r),
        id);
  }

  private List<Payment> billPayments(String id) {
    return db.query("SELECT t.* FROM transactions t WHERE t.target_id=? AND (t.record_kind='PRODUCT_HISTORY' OR"
            + " (t.record_kind='PAYMENT' AND t.operation='BILL_PAYMENT' AND t.status='SUCCESS'"
            + " AND EXISTS (SELECT 1 FROM journal_entries j WHERE j.transaction_id=t.id AND j.status='POSTED')))"
            + " ORDER BY t.created_at DESC,t.id DESC",
        (r, n) -> payment(r), id);
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
