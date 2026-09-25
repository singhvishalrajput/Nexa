package com.nexa.api.service;

import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates customer payment records and lets their owner set their state. */
@Service
@Transactional
public class PaymentItemService {
  private static final Set<String> BILL_STATES = Set.of("UPCOMING", "DUE", "OVERDUE", "PAID", "FAILED");
  private static final Set<String> MANDATE_STATES = Set.of("PENDING", "ACTIVE", "PAUSED", "CANCELLED", "EXPIRED", "ACTION_REQUIRED");
  private final JdbcTemplate db;
  private final CurrentUserProvider user;

  public PaymentItemService(JdbcTemplate db, CurrentUserProvider user) {
    this.db = db;
    this.user = user;
  }

  public record BillRequest(String billerName, BigDecimal amount, BigDecimal minimumAmount, LocalDate dueAt, String category, String customerNumber) {}
  public record StatusRequest(String status) {}

  public Map<String, Object> createBill(BillRequest request) {
    if (request == null || blank(request.billerName(), 160) || request.amount() == null || request.amount().signum() <= 0
        || request.amount().scale() > 2 || request.minimumAmount() != null && (request.minimumAmount().signum() < 0 || request.minimumAmount().compareTo(request.amount()) > 0)
        || request.dueAt() == null || blank(request.category(), 80) || blank(request.customerNumber(), 80))
      throw new InvalidRequestException("Supply a biller, amount, due date, category and customer number");
    String id = "B-" + UUID.randomUUID();
    db.update("INSERT INTO transactions(id,record_kind,user_id,display_name,amount,minimum_amount,currency_code,due_at,status,category,destination_masked,transaction_reference,created_at,updated_at) VALUES(?,'BILL',?,?,?,?,?,?,'UPCOMING',?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        id, user.userId(), request.billerName().trim(), request.amount(), request.minimumAmount(), "INR", request.dueAt().toString(), request.category().trim(), request.customerNumber().trim(), id);
    return bill(id);
  }

  public Map<String, Object> setBillStatus(String id, StatusRequest request) {
    return setStatus(id, "BILL", BILL_STATES, request);
  }

  public Map<String, Object> setMandateStatus(String id, StatusRequest request) {
    return setStatus(id, "MANDATE", MANDATE_STATES, request);
  }

  private Map<String, Object> setStatus(String id, String kind, Set<String> states, StatusRequest request) {
    String status = request == null || request.status() == null ? "" : request.status().trim().toUpperCase();
    if (!states.contains(status)) throw new InvalidRequestException("Invalid status");
    int updated = db.update("UPDATE transactions SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND record_kind=? AND user_id=?", status, id, kind, user.userId());
    if (updated == 0) throw new ResourceNotFoundException("Payment item not found");
    return kind.equals("BILL") ? bill(id) : mandate(id);
  }

  private Map<String, Object> bill(String id) { return db.queryForMap("SELECT * FROM transactions WHERE id=? AND record_kind='BILL' AND user_id=?", id, user.userId()); }
  private Map<String, Object> mandate(String id) { return db.queryForMap("SELECT * FROM transactions WHERE id=? AND record_kind='MANDATE' AND user_id=?", id, user.userId()); }
  private boolean blank(String value, int length) { return value == null || value.isBlank() || value.trim().length() > length; }
}
