package com.nexa.api.service;

import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ConflictException;
import com.nexa.api.exep.ResourceNotFoundException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates tracked bills and manages mandate state. Bill state follows dates and posted payments. */
@Service
@Transactional
public class PaymentItemService {
  // Keep new bills payable within the same per-payment limit used by BillPaymentService.
  private static final BigDecimal MAX_BILL_AMOUNT = new BigDecimal("9999999999999.99");
  private static final Set<String> MANDATE_STATES = Set.of("PENDING", "ACTIVE", "PAUSED", "CANCELLED", "EXPIRED", "ACTION_REQUIRED");
  private final JdbcTemplate db;
  private final CurrentUserProvider user;
  private final Clock clock;
  private final BusinessDateResolver dates;

  public PaymentItemService(JdbcTemplate db, CurrentUserProvider user, Clock clock, BusinessDateResolver dates) {
    this.db = db;
    this.user = user;
    this.clock = clock;
    this.dates = dates;
  }

  public record BillRequest(String billerName, BigDecimal amount, BigDecimal minimumAmount,
      LocalDate dueAt, String category, String customerNumber, String payeeId) {
    public BillRequest(String billerName, BigDecimal amount, BigDecimal minimumAmount,
        LocalDate dueAt, String category, String customerNumber) {
      this(billerName, amount, minimumAmount, dueAt, category, customerNumber, null);
    }
  }
  public record StatusRequest(String status) {}

  public String createBill(BillRequest request) {
    if (request == null) throw new InvalidRequestException("Enter the bill details.");
    String biller = billText(request.billerName(), "Biller name", 160, 200);
    String category = billText(request.category(), "Category", 80, 80);
    String customerNumber = billText(request.customerNumber(), "Customer number", 40, 40);
    if (!validMoney(request.amount()) || request.amount().signum() <= 0)
      throw new InvalidRequestException("Amount must be between 0.01 and 9999999999999.99 with at most two decimal places.");
    if (request.minimumAmount() != null
        && (!validMoney(request.minimumAmount()) || request.minimumAmount().signum() < 0
            || request.minimumAmount().compareTo(request.amount()) > 0))
      throw new InvalidRequestException("Minimum amount must be between zero and the bill amount with at most two decimal places.");
    if (request.dueAt() == null || request.dueAt().getYear() < 1 || request.dueAt().getYear() > 9999)
      throw new InvalidRequestException("Enter a valid due date between years 0001 and 9999.");
    LocalDate today = LocalDate.now(clock.withZone(dates.zone()));
    String status = request.dueAt().isBefore(today) ? "OVERDUE" : request.dueAt().equals(today) ? "DUE" : "UPCOMING";
    String owner = user.userId();
    Long destination = request.payeeId() == null ? null : billRecipient(request.payeeId(), owner);
    String id = "B-" + UUID.randomUUID();
    db.update("INSERT INTO transactions(id,record_kind,user_id,display_name,amount,minimum_amount,currency_code,due_at,status,category,destination_masked,transaction_reference,target_id,destination_account_id,created_at,updated_at) VALUES(?,'BILL',?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        id, owner, biller, request.amount(), request.minimumAmount(), "INR", request.dueAt().toString(), status, category, customerNumber, id, request.payeeId(), destination);
    return id;
  }

  private record SavedRecipient(Long destination, String status, String fingerprint) {}

  /** Bind a saved recipient without moving money; confirmation rechecks the whole route. */
  private Long billRecipient(String payeeId, String owner) {
    if (payeeId.isBlank() || payeeId.length() > 40 || payeeId.startsWith("EP-"))
      throw new InvalidRequestException("Choose a saved Nexa payee for this bill.");
    var payees = db.query("SELECT destination_account_id,status,destination_hash FROM transactions"
            + " WHERE id=? AND record_kind='BENEFICIARY' AND user_id=? FOR UPDATE",
        (rs, row) -> new SavedRecipient(rs.getObject(1, Long.class), rs.getString(2), rs.getString(3)),
        payeeId, owner);
    if (payees.isEmpty() || payees.get(0).destination() == null || !"ACTIVE".equals(payees.get(0).status()))
      throw new InvalidRequestException("Choose an active saved payee linked to a Nexa account.");
    SavedRecipient payee = payees.get(0);
    String expected;
    try {
      expected = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
          .digest(("NEXA:" + payee.destination()).getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    if (!expected.equals(payee.fingerprint()))
      throw new InvalidRequestException("This payee's Nexa account needs verification. Save their full account number in Payees first.");
    var destinations = db.queryForList("SELECT id FROM accounts WHERE id=? AND account_category='CUSTOMER'"
        + " AND account_type IN ('SAVINGS','CURRENT') AND status='ACTIVE' AND currency_code='INR' FOR UPDATE",
        Long.class, payee.destination());
    if (destinations.isEmpty())
      throw new InvalidRequestException("The bill recipient must have an active Nexa INR savings or current account.");
    return payee.destination();
  }

  public Map<String, Object> setBillStatus(String id, StatusRequest request) {
    Integer count = db.queryForObject("SELECT COUNT(*) FROM transactions p JOIN customers c ON c.user_id=p.user_id"
            + " LEFT JOIN accounts a ON a.id=p.source_account_id WHERE p.id=? AND p.record_kind='BILL'"
            + " AND p.user_id=? AND (p.source_account_id IS NULL OR a.customer_id=c.id)",
        Integer.class, id, user.userId());
    if (count == null || count == 0) throw new ResourceNotFoundException("Payment item not found");
    throw new ConflictException("Bill status is managed automatically from its due date and completed payments.");
  }

  public Map<String, Object> setMandateStatus(String id, StatusRequest request) {
    return setStatus(id, "MANDATE", MANDATE_STATES, request);
  }

  private Map<String, Object> setStatus(String id, String kind, Set<String> states, StatusRequest request) {
    String status = request == null || request.status() == null ? "" : request.status().trim().toUpperCase();
    if (!states.contains(status)) throw new InvalidRequestException("Invalid status");
    int updated = db.update("UPDATE transactions SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND record_kind=? AND user_id=?", status, id, kind, user.userId());
    if (updated == 0) throw new ResourceNotFoundException("Payment item not found");
    return mandate(id);
  }

  private Map<String, Object> mandate(String id) { return db.queryForMap("SELECT * FROM transactions WHERE id=? AND record_kind='MANDATE' AND user_id=?", id, user.userId()); }
  private static boolean validMoney(BigDecimal value) {
    return value != null && value.scale() <= 2 && value.compareTo(MAX_BILL_AMOUNT) <= 0;
  }

  private static String billText(String value, String field, int maxCharacters, int maxBytes) {
    if (value == null || value.isBlank()) throw new InvalidRequestException(field + " is required.");
    String trimmed = value.strip();
    if (trimmed.length() > maxCharacters)
      throw new InvalidRequestException(field + " must contain at most " + maxCharacters + " characters.");
    // The existing Oracle VARCHAR2 columns may use BYTE length semantics.
    if (trimmed.getBytes(StandardCharsets.UTF_8).length > maxBytes)
      throw new InvalidRequestException(field + " is too long. Shorten it and try again.");
    return trimmed;
  }
}
