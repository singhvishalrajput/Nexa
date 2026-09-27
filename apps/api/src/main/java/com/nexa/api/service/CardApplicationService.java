package com.nexa.api.service;

import com.nexa.api.beans.BankingModels;
import com.nexa.api.exep.ConflictException;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persisted local card applications and controls. This is not payment-network card issuing. */
@Service
@Transactional
public class CardApplicationService {
  private final JdbcTemplate db;
  private final CurrentUserProvider user;
  private final CardQueryService cards;
  private final Clock clock;

  public CardApplicationService(JdbcTemplate db, CurrentUserProvider user, CardQueryService cards, Clock clock) {
    this.db = db;
    this.user = user;
    this.cards = cards;
    this.clock = clock;
  }

  public record ApplicationRequest(long accountId, String cardType, String requestId, String displayName) {}
  public record Decision(BigDecimal creditLimit, String reason) {}
  public record Application(String id, String displayName, String cardType, String status,
      long accountId, String applicantName, String applicantUserId, String createdAt,
      BigDecimal creditLimit, String reviewedAt, String reviewReason) {}
  private record Owner(long id, String name) {}
  private record CardRow(long id, String productId, long customerId, long fundingId,
      String type, String state, String bankState, String requestId, String fingerprint,
      BigDecimal limit, String reason, int customerBlock) {}

  private Owner authorize(String role, boolean lock) {
    var rows = db.query("SELECT id,full_name FROM customers WHERE user_id=? AND role=? AND status='ACTIVE'"
        + (lock ? " FOR UPDATE" : ""), (r, n) -> new Owner(r.getLong(1), r.getString(2)), user.userId(), role);
    if (rows.isEmpty()) throw new AccessDeniedException("An active " + role.toLowerCase() + " account is required.");
    return rows.get(0);
  }

  private void funding(long accountId, long ownerId) {
    var rows = db.queryForList("SELECT a.id FROM accounts a JOIN customers c ON c.id=a.customer_id"
        + " WHERE a.id=? AND a.customer_id=? AND a.account_category='CUSTOMER'"
        + " AND a.account_type IN ('SAVINGS','CURRENT') AND a.status='ACTIVE' AND a.currency_code='INR'"
        + " AND c.status='ACTIVE' AND c.role='CUSTOMER' FOR UPDATE OF a.balance", Long.class, accountId, ownerId);
    if (rows.isEmpty()) throw new InvalidRequestException("Choose your active Nexa INR savings or current account.");
  }

  public BankingModels.Card apply(ApplicationRequest r) {
    if (r == null) throw new InvalidRequestException("Enter the card application details.");
    Owner owner = authorize("CUSTOMER", true);
    String kind = r.cardType() == null ? "" : r.cardType().trim().toUpperCase(java.util.Locale.ROOT);
    if (!Set.of("DEBIT", "CREDIT").contains(kind)) throw new InvalidRequestException("Choose a debit or credit card.");
    String key;
    try {
      key = UUID.fromString(r.requestId()).toString();
      if (!key.equalsIgnoreCase(r.requestId())) throw new IllegalArgumentException();
    } catch (Exception invalid) { throw new InvalidRequestException("Supply a UUID requestId."); }
    String name = r.displayName() == null || r.displayName().isBlank()
        ? "Nexa " + kind.toLowerCase(java.util.Locale.ROOT) + " card" : r.displayName().strip();
    if (name.length() > 100 || name.getBytes(StandardCharsets.UTF_8).length > 120)
      throw new InvalidRequestException("Card name must contain at most 100 characters and 120 UTF-8 bytes.");
    String fingerprint = hash(r.accountId() + "\n" + kind + "\n" + name);
    var previous = db.query("SELECT * FROM accounts WHERE card_request_id=?", this::cardRow, key);
    if (!previous.isEmpty()) {
      CardRow card = previous.get(0);
      if (card.customerId() != owner.id() || !fingerprint.equals(card.fingerprint()))
        throw new ConflictException("This request ID was already used with different application details.");
      return cards.detail(card.productId());
    }
    funding(r.accountId(), owner.id());
    Integer count = db.queryForObject("SELECT COUNT(*) FROM accounts WHERE account_type='CARD'"
        + " AND funding_account_id=? AND product_type=? AND status<>'CLOSED'"
        + " AND COALESCE(product_status,'ACTIVE') NOT IN ('REJECTED','CLOSED','EXPIRED','CANCELLED')",
        Integer.class, r.accountId(), kind);
    if (count != null && count > 0)
      throw new ConflictException("This account already has a usable or pending " + kind.toLowerCase(java.util.Locale.ROOT) + " card.");
    String product = "C-" + UUID.randomUUID();
    String state = "CREDIT".equals(kind) ? "PENDING_APPROVAL" : "ACTIVE";
    Timestamp now = Timestamp.from(clock.instant());
    // These are explicitly internal references, never plausible full card numbers.
    String reference = "LOCAL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    String last4 = String.format(java.util.Locale.ROOT, "%04d", java.util.concurrent.ThreadLocalRandom.current().nextInt(10000));
    try {
      db.update("INSERT INTO accounts(account_number,customer_id,account_name,account_type,account_category,"
          + "currency_code,balance,status,version,created_at,updated_at,product_id,funding_account_id,product_status,"
          + "product_type,number_masked,credit_limit,minimum_payment,card_request_id,card_request_hash,card_slot_key)"
          + " VALUES(?,?,?,'CARD','CUSTOMER','INR',0,?,0,?,?,?,?,?,?,?,0,0,?,?,?)",
          reference, owner.id(), name, "CREDIT".equals(kind) ? "BLOCKED" : "ACTIVE", now, now, product,
          r.accountId(), state, kind, "LOCAL " + last4, key, fingerprint, kind + ":" + r.accountId());
    } catch (org.springframework.dao.DuplicateKeyException duplicate) {
      throw new ConflictException("A card application for this account or request already exists. Refresh your cards.");
    }
    CardRow created = requireCard(product, false);
    audit(created, "CARD_APPLICATION", null, state, "Local " + kind.toLowerCase(java.util.Locale.ROOT) + " card application", null);
    return cards.detail(product);
  }

  public BankingModels.Card setBlocked(String id, boolean blocked) {
    Owner owner = authorize("CUSTOMER", true);
    CardRow card = requireCard(id, true);
    if (card.customerId() != owner.id()) throw new ResourceNotFoundException("Card not found.");
    if (!Set.of("ACTIVE", "BLOCKED").contains(card.state()))
      throw new ConflictException("Only an approved, active card can be blocked or unblocked.");
    // Product state is the customer control; account state remains the bank's independent restriction.
    if (!"ACTIVE".equals(card.bankState()))
      throw new ConflictException("This card has a bank restriction. Contact the administrator.");
    funding(card.fundingId(), owner.id());
    String target = blocked ? "BLOCKED" : "ACTIVE";
    if (target.equals(card.state())) return cards.detail(id);
    if (!blocked && card.customerBlock() != 1)
      throw new ConflictException("Only a card you blocked here can be unblocked here.");
    db.update("UPDATE accounts SET product_status=?,card_customer_block=?,version=version+1,updated_at=? WHERE id=?",
        target, blocked ? 1 : 0, Timestamp.from(clock.instant()), card.id());
    audit(card, blocked ? "CARD_BLOCK" : "CARD_UNBLOCK", card.state(), target, "Customer card control", null);
    return cards.detail(id);
  }

  @Transactional(readOnly = true)
  public List<Application> applications(String status) {
    authorize("ADMIN", false);
    String state = status == null || status.isBlank() || "ALL".equals(status) ? null : status;
    if (state != null && !Set.of("PENDING_APPROVAL", "ACTIVE", "BLOCKED", "REJECTED", "CLOSED").contains(state))
      throw new InvalidRequestException("Choose a valid card application status.");
    return db.query(applicationSql() + " AND (? IS NULL OR a.product_status=?) ORDER BY a.created_at DESC,a.id DESC",
        this::applicationRow, state, state);
  }

  @Transactional(readOnly = true)
  public Application application(String id) {
    authorize("ADMIN", false);
    return applicationView(id);
  }

  public Application decide(String id, Decision r, boolean approve) {
    authorize("ADMIN", true);
    if (r == null) throw new InvalidRequestException("Enter the review decision.");
    String reason = r.reason() == null || r.reason().isBlank() ? null : r.reason().strip();
    if (reason != null && (reason.length() > 500 || reason.getBytes(StandardCharsets.UTF_8).length > 500))
      throw new InvalidRequestException("Review reason must contain at most 500 UTF-8 bytes.");
    if (!approve && reason == null) throw new InvalidRequestException("Enter a reason for rejection.");
    BigDecimal limit = approve ? r.creditLimit() : BigDecimal.ZERO;
    if (approve && (limit == null || limit.signum() <= 0 || limit.scale() > 2 || limit.precision() - limit.scale() > 13))
      throw new InvalidRequestException("Set a positive credit limit with at most 13 integer digits and two decimal places.");
    // Match customer controls' owner -> card -> funding lock order; a concurrent access
    // revocation cannot pass an approval's customer check and commit before issuance.
    CardRow lookup = requireCard(id, false);
    if (approve) {
      var owners = db.queryForList("SELECT id FROM customers WHERE id=? AND role='CUSTOMER' AND status='ACTIVE' FOR UPDATE",
          Long.class, lookup.customerId());
      if (owners.isEmpty()) throw new ConflictException("The applicant must be an active customer before approval.");
    }
    CardRow card = requireCard(id, true);
    if (!"CREDIT".equals(card.type()) || card.requestId() == null)
      throw new ConflictException("Only a new credit card application can be reviewed here.");
    String target = approve ? "ACTIVE" : "REJECTED";
    if (!"PENDING_APPROVAL".equals(card.state())) {
      // A successful approval may since have been blocked by its customer; the original decision is still final.
      boolean sameDecision = approve && Set.of("ACTIVE", "BLOCKED").contains(card.state())
          || !approve && "REJECTED".equals(card.state());
      if (sameDecision && card.limit().compareTo(limit) == 0 && Objects.equals(reason, card.reason()))
        return applicationView(id);
      throw new ConflictException("This card application already has a different decision.");
    }
    if (approve) {
      if ("CLOSED".equals(card.bankState())) throw new ConflictException("A closed card account cannot be approved.");
      // BLOCKED is the initial pending credit-account state. An explicit administrative
      // account restriction is independent and must not be overwritten by card approval.
      Integer restrictions = db.queryForObject("SELECT COUNT(*) FROM transactions WHERE record_kind='ADMIN_EVENT'"
          + " AND source_account_id=? AND operation='UPDATE_ACCOUNT' AND after_status='BLOCKED'", Integer.class, card.id());
      if ("BLOCKED".equals(card.bankState()) && restrictions != null && restrictions > 0)
        throw new ConflictException("This card account has an administrator restriction. Resolve it before approval.");
      funding(card.fundingId(), card.customerId());
    }
    Timestamp now = Timestamp.from(clock.instant());
    db.update("UPDATE accounts SET product_status=?,status=?,credit_limit=?,reviewed_by=?,review_reason=?,reviewed_at=?,"
        + "approved_at=?,card_slot_key=?,version=version+1,updated_at=? WHERE id=?",
        target, approve ? "ACTIVE" : "CLOSED", limit, user.userId(), reason, now, approve ? now : null,
        approve ? "CREDIT:" + card.fundingId() : null, now, card.id());
    audit(card, approve ? "CARD_APPROVE" : "CARD_REJECT", card.state(), target, reason, approve ? limit : null);
    return applicationView(id);
  }

  private CardRow requireCard(String id, boolean lock) {
    var rows = db.query("SELECT * FROM accounts WHERE account_type='CARD' AND product_id=?"
        + (lock ? " FOR UPDATE" : ""), this::cardRow, id);
    if (rows.isEmpty()) throw new ResourceNotFoundException("Card not found.");
    return rows.get(0);
  }
  private CardRow cardRow(ResultSet r, int row) throws SQLException {
    return new CardRow(r.getLong("id"), r.getString("product_id"), r.getLong("customer_id"),
        r.getLong("funding_account_id"), r.getString("product_type"), r.getString("product_status"),
        r.getString("status"), r.getString("card_request_id"), r.getString("card_request_hash"),
        r.getBigDecimal("credit_limit"), r.getString("review_reason"), r.getInt("card_customer_block"));
  }
  private String applicationSql() {
    return "SELECT a.*,c.full_name,c.user_id FROM accounts a JOIN customers c ON c.id=a.customer_id"
        + " WHERE a.account_type='CARD' AND a.card_request_id IS NOT NULL";
  }
  private Application applicationRow(ResultSet r, int row) throws SQLException {
    return new Application(r.getString("product_id"), r.getString("account_name"), r.getString("product_type"),
        r.getString("product_status"), r.getLong("funding_account_id"), r.getString("full_name"), r.getString("user_id"),
        instant(r.getTimestamp("created_at")), r.getBigDecimal("credit_limit"), instant(r.getTimestamp("reviewed_at")), r.getString("review_reason"));
  }
  private Application applicationView(String id) {
    var rows = db.query(applicationSql() + " AND a.product_id=?", this::applicationRow, id);
    if (rows.isEmpty()) throw new ResourceNotFoundException("Card application not found.");
    return rows.get(0);
  }
  private String instant(Timestamp time) { return time == null ? null : time.toInstant().toString(); }
  private void audit(CardRow card, String operation, String before, String after, String reason, BigDecimal limit) {
    db.update("INSERT INTO transactions(id,record_kind,user_id,source_account_id,target_id,operation,status,"
        + "audit_reason,before_status,after_status,amount,currency_code,created_at) VALUES(?,'ADMIN_EVENT',?,?,?,?,"
        + "'COMPLETED',?,?,?,?, 'INR',?)", "A-" + UUID.randomUUID(), user.userId(), card.id(), card.productId(),
        operation, reason, before, after, limit, Timestamp.from(clock.instant()));
  }
  private static String hash(String value) {
    try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }
}
