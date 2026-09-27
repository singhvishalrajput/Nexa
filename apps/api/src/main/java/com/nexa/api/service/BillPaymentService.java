package com.nexa.api.service;

import com.nexa.api.beans.Account;
import com.nexa.api.beans.AccountCategory;
import com.nexa.api.beans.AccountStatus;
import com.nexa.api.beans.AccountType;
import com.nexa.api.beans.TransactionRequest;
import com.nexa.api.exep.ConflictException;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import com.nexa.api.repository.AccountDao;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A bill review authorizes one internal transfer to an explicitly saved Nexa payee. */
@Service
public class BillPaymentService {
  private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");
  public record PrepareRequest(
      @NotNull UUID requestKey,
      @NotBlank @Size(max = 40) String billId,
      @NotBlank @Pattern(regexp = "[0-9]{1,19}") String sourceAccountId,
      @Size(max = 40) String payeeId,
      @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount) {}

  public record Receipt(
      String id, String billId, String billerName, String sourceAccountId, String sourceName,
      String sourceMasked, String payeeId, String recipientName, String destinationMasked,
      String amount, String currencyCode, String status, String reference, String expiresAt,
      String completedAt, String failureReason) {}

  private record Bill(String id, String name, String category, BigDecimal amount,
      BigDecimal minimum, String currency, String status, String payeeId, Long destinationId) {}
  private record Attempt(Receipt receipt, long destinationId, BigDecimal originalRemaining,
      String fingerprint) {}
  private record Route(Account source, Account destination) {}
  /** Raised only by our preflight checks, never by a posting or persistence operation. */
  private static final class ReviewRejected extends RuntimeException {
    ReviewRejected(String message) { super(message); }
  }

  private final JdbcTemplate db;
  private final CurrentUserProvider user;
  private final AccountDao accounts;
  private final TransactionService transactions;
  private final EntityManager entities;
  private final Clock clock;

  public BillPaymentService(JdbcTemplate db, CurrentUserProvider user, AccountDao accounts,
      TransactionService transactions, EntityManager entities, Clock clock) {
    this.db = db;
    this.user = user;
    this.accounts = accounts;
    this.transactions = transactions;
    this.entities = entities;
    this.clock = clock;
  }

  // These domain exceptions are raised only before any prepare writes. Chat may catch them
  // to request corrected details inside its own transaction without marking it rollback-only.
  @Transactional(noRollbackFor = {InvalidRequestException.class, ResourceNotFoundException.class, ConflictException.class})
  public Receipt prepare(PrepareRequest request) {
    validateRequest(request);
    String owner = user.userId();
    String id = "BP-" + request.requestKey();
    String fingerprint = fingerprint(request);
    // Reject a reused key for a different bill before taking that other bill's lock.
    var priorKey = attempts("id=? AND user_id=?", false, id, owner);
    if (!priorKey.isEmpty() && !request.billId().equals(priorKey.get(0).receipt().billId()))
      throw new ConflictException("This request key was already used for different payment details.");
    Bill bill = bill(request.billId(), owner, true);
    List<Attempt> existing = attempts("id=? AND user_id=?", true, id, owner);
    if (!existing.isEmpty()) {
      Attempt prior = existing.get(0);
      if (!fingerprint.equals(prior.fingerprint()))
        throw new ConflictException("This request key was already used for different payment details.");
      return expire(prior, owner);
    }
    if (db.queryForObject("SELECT COUNT(*) FROM bill_payment_attempts WHERE id=?", Integer.class, id) != 0)
      throw new ConflictException("This request key is already in use. Start a new payment review.");

    BigDecimal remaining = remaining(bill);
    BigDecimal amount = request.amount() == null ? remaining : request.amount();
    String payeeId = request.payeeId() == null ? bill.payeeId() : request.payeeId();
    Route route;
    try {
      payable(bill, remaining);
      validateAmount(bill, remaining, amount);
      long destination = payeeDestination(payeeId, owner);
      if (!Objects.equals(bill.destinationId(), destination) && hasCompletedPayment(bill.id()))
        throw new ReviewRejected("This bill already has a payment. Its recipient cannot be changed.");
      route = route(Long.parseLong(request.sourceAccountId()), destination, owner, amount);
    } catch (ReviewRejected rejected) {
      throw new InvalidRequestException(rejected.getMessage());
    }

    if (!Objects.equals(bill.payeeId(), payeeId)
        || !Objects.equals(bill.destinationId(), route.destination().getId())) {
      // A bill lock serializes routing edits with all confirmations for this bill.
      db.update("UPDATE bill_payment_attempts SET status='FAILED',failure_reason=?,updated_at=?"
              + " WHERE bill_id=? AND user_id=? AND status='READY'",
          "The bill's payee changed. Review the new recipient before paying.",
          Timestamp.from(clock.instant()), bill.id(), owner);
      db.update("UPDATE transactions SET target_id=?,destination_account_id=?,updated_at=?"
              + " WHERE id=? AND record_kind='BILL' AND user_id=?",
          payeeId, route.destination().getId(), Timestamp.from(clock.instant()), bill.id(), owner);
    }
    Instant now = clock.instant();
    db.update("INSERT INTO bill_payment_attempts"
            + " (id,request_key,request_fingerprint,user_id,bill_id,biller_name,source_account_id,"
            + "source_name,source_masked,payee_id,destination_account_id,recipient_name,destination_masked,"
            + "amount,original_remaining,currency_code,status,created_at,updated_at,expires_at)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'INR','READY',?,?,?)",
        id, request.requestKey().toString(), fingerprint, owner, bill.id(), bill.name(),
        route.source().getId(), route.source().getAccountName(), mask(route.source()), payeeId,
        route.destination().getId(), route.destination().getCustomer().getFullName(), mask(route.destination()),
        amount, remaining, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.plusSeconds(300)));
    return find(id, owner, false).receipt();
  }

  @Transactional
  public Receipt status(String id) {
    String owner = user.userId();
    Attempt initial = find(id, owner, false);
    bill(initial.receipt().billId(), owner, true);
    return expire(find(id, owner, true), owner);
  }

  @Transactional(readOnly = true)
  public List<Receipt> history(String billId) {
    String owner = user.userId();
    bill(billId, owner, false);
    return attempts("bill_id=? AND user_id=? ORDER BY created_at DESC,id DESC FETCH NEXT 100 ROWS ONLY",
        false, billId, owner).stream().map(a -> visible(a.receipt())).toList();
  }

  @Transactional
  public Receipt cancel(String id) {
    String owner = user.userId();
    Attempt initial = find(id, owner, false);
    bill(initial.receipt().billId(), owner, true);
    Attempt attempt = find(id, owner, true);
    Receipt receipt = expire(attempt, owner);
    if (!"READY".equals(receipt.status())) return receipt;
    return terminal(attempt, owner, "CANCELLED", "You cancelled this payment review.");
  }

  @Transactional
  public Receipt confirm(String id) {
    String owner = user.userId();
    Attempt initial = find(id, owner, false);
    // Every path uses bill -> attempt -> saved payee -> sorted account locks.
    Bill bill = bill(initial.receipt().billId(), owner, true);
    Attempt attempt = find(id, owner, true);
    Receipt receipt = expire(attempt, owner);
    if (!"READY".equals(receipt.status())) return receipt;
    BigDecimal amount = new BigDecimal(receipt.amount());
    BigDecimal remaining = remaining(bill);
    Route route;
    try {
      payable(bill, remaining);
      if (remaining.compareTo(attempt.originalRemaining()) != 0)
        throw new ReviewRejected("The bill's outstanding amount changed. Start a new payment review.");
      if (!Objects.equals(bill.payeeId(), receipt.payeeId())
          || !Objects.equals(bill.destinationId(), attempt.destinationId()))
        throw new ReviewRejected("The bill's payee changed. Review the new recipient before paying.");
      validateAmount(bill, remaining, amount);
      long destination = payeeDestination(receipt.payeeId(), owner);
      if (destination != attempt.destinationId())
        throw new ReviewRejected("The saved payee's account changed. Start a new payment review.");
      route = route(Long.parseLong(receipt.sourceAccountId()), destination, owner, amount);
    } catch (ReviewRejected rejected) {
      // Only explicit checks above reach here; no ledger writes have started.
      return terminal(attempt, owner, "FAILED", rejected.getMessage());
    }

    TransactionRequest transfer = new TransactionRequest();
    transfer.setSourceAccountId(route.source().getId());
    transfer.setDestinationAccountId(route.destination().getId());
    transfer.setAmount(amount);
    var posting = transactions.transfer(transfer);
    entities.flush();
    requireOne(db.update("UPDATE transactions SET operation='BILL_PAYMENT',target_id=?,parent_id=?,"
            + "merchant_name=?,display_name=?,transaction_reference=?,category=?,"
            + "payment_method='INTERNAL_TRANSFER',user_id=?,currency_code='INR' WHERE id=?",
        bill.id(), bill.id(), bill.name(), bill.name(), posting.getId(), bill.category(), owner, posting.getId()));
    Instant completed = clock.instant();
    requireOne(db.update("UPDATE bill_payment_attempts SET status='COMPLETED',transaction_reference=?,"
            + "completed_at=?,updated_at=? WHERE id=? AND user_id=? AND status='READY'",
        posting.getId(), Timestamp.from(completed), Timestamp.from(completed), id, owner));
    // PAID is derived from posted totals. Stored historical PAID is an independent
    // legacy stop flag, and must not be written for new ledger-backed payments.
    requireOne(db.update("UPDATE transactions SET updated_at=? WHERE id=? AND record_kind='BILL' AND user_id=?",
        Timestamp.from(completed), bill.id(), owner));
    // Any error from the transfer onwards rolls back balances, ledger, metadata and receipt together.
    return find(id, owner, false).receipt();
  }

  private Route route(long sourceId, long destinationId, String owner, BigDecimal amount) {
    if (sourceId == destinationId) throw new ReviewRejected("Choose a source account different from the payee's account.");
    Account first = lockedAccount(Math.min(sourceId, destinationId));
    Account second = lockedAccount(Math.max(sourceId, destinationId));
    Account source = first.getId() == sourceId ? first : second;
    Account destination = first.getId() == sourceId ? second : first;
    if (source.getCustomer() == null || !owner.equals(source.getCustomer().getUserId()))
      throw new ReviewRejected("Choose one of your own active Nexa accounts.");
    for (Account account : List.of(source, destination)) {
      if (account.getAccountCategory() != AccountCategory.CUSTOMER || account.getCustomer() == null
          || (account.getAccountType() != AccountType.SAVINGS && account.getAccountType() != AccountType.CURRENT))
        throw new ReviewRejected("Bill payments require Nexa customer deposit accounts.");
      if (account.getStatus() != AccountStatus.ACTIVE)
        throw new ReviewRejected("Both accounts must be active before this bill can be paid.");
      if (!"INR".equals(account.getCurrencyCode()))
        throw new ReviewRejected("Bill payments support Indian rupee accounts only.");
    }
    if (source.getBalance().compareTo(amount) < 0)
      throw new ReviewRejected("There is not enough money in this account. Add funds or choose another account, then start a new review.");
    return new Route(source, destination);
  }

  private Account lockedAccount(long id) {
    Account account = accounts.findLockedById(id)
        .orElseThrow(() -> new ReviewRejected("A payment account is no longer available. Start a new payment review."));
    entities.refresh(account);
    return account;
  }

  private long payeeDestination(String payeeId, String owner) {
    if (payeeId == null || payeeId.isBlank())
      throw new ReviewRejected("Choose a saved payee linked to the biller's Nexa account before paying.");
    var rows = db.query("SELECT destination_account_id,status,destination_hash FROM transactions"
            + " WHERE id=? AND record_kind='BENEFICIARY' AND user_id=? FOR UPDATE",
        (r, n) -> new Object[] {r.getObject(1, Long.class), r.getString(2), r.getString(3)}, payeeId, owner);
    if (rows.isEmpty() || rows.get(0)[0] == null || !"ACTIVE".equals(rows.get(0)[1]))
      throw new ReviewRejected("This payee is not linked to an active saved Nexa account. Update Payees and start a new review.");
    long destination = (Long) rows.get(0)[0];
    if (!sha256("NEXA:" + destination).equals(rows.get(0)[2]))
      throw new ReviewRejected("This payee's Nexa account needs verification. Save their full account number in Payees first.");
    return destination;
  }

  private BigDecimal paid(String billId) {
    return db.queryForObject("SELECT COALESCE(SUM(t.amount),0) FROM transactions t"
            + " WHERE t.record_kind='PAYMENT' AND t.operation='BILL_PAYMENT' AND t.target_id=?"
            + " AND t.status='SUCCESS' AND EXISTS (SELECT 1 FROM journal_entries j"
            + " WHERE j.transaction_id=t.id AND j.status='POSTED')", BigDecimal.class, billId);
  }

  private boolean hasCompletedPayment(String billId) {
    // Reversing a journal can reopen the amount due, but cannot erase the agreed recipient.
    return paid(billId).signum() > 0
        || db.queryForObject("SELECT COUNT(*) FROM bill_payment_attempts WHERE bill_id=? AND status='COMPLETED'",
            Long.class, billId) > 0;
  }

  private BigDecimal remaining(Bill bill) {
    if (bill.amount() == null) return BigDecimal.ZERO;
    return bill.amount().subtract(paid(bill.id())).max(BigDecimal.ZERO);
  }

  private static void payable(Bill bill, BigDecimal remaining) {
    if ("PAID".equals(bill.status()) || remaining.signum() <= 0)
      throw new ReviewRejected("This bill is already paid. No further payment is needed.");
    if (!"INR".equals(bill.currency()))
      throw new ReviewRejected("Only bills in Indian rupees can be paid here.");
  }

  private static void validateAmount(Bill bill, BigDecimal remaining, BigDecimal amount) {
    if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.compareTo(MAX_AMOUNT) > 0)
      throw new ReviewRejected("Enter a positive amount with up to thirteen integer digits and two decimal places.");
    if (amount.compareTo(remaining) > 0)
      throw new ReviewRejected("The payment cannot exceed this bill's outstanding amount.");
    BigDecimal minimum = bill.minimum() == null ? new BigDecimal("0.01")
        : bill.minimum().max(new BigDecimal("0.01"));
    if (amount.compareTo(minimum.min(remaining)) < 0)
      throw new ReviewRejected("Pay at least the minimum amount, or the full remaining amount if it is smaller.");
  }

  private static void validateRequest(PrepareRequest request) {
    if (request == null || request.requestKey() == null || request.billId() == null
        || request.billId().isBlank() || request.billId().length() > 40
        || request.sourceAccountId() == null || !request.sourceAccountId().matches("[0-9]{1,19}")
        || (request.payeeId() != null && (request.payeeId().isBlank() || request.payeeId().length() > 40)))
      throw new InvalidRequestException("Choose a bill, your source account and a valid request key.");
    try {
      if (Long.parseLong(request.sourceAccountId()) <= 0) throw new NumberFormatException();
    } catch (NumberFormatException error) {
      throw new InvalidRequestException("Choose a valid source account.");
    }
    // Bound the value before fingerprint() formats it. Precision-minus-scale can overflow
    // for adversarial exponents, and toPlainString() must never expand those inputs.
    if (request.amount() != null && (request.amount().signum() <= 0 || request.amount().scale() > 2
        || request.amount().compareTo(MAX_AMOUNT) > 0))
      throw new InvalidRequestException("Enter a positive amount with up to thirteen integer digits and two decimal places.");
  }

  private Bill bill(String id, String owner, boolean lock) {
    var rows = db.query("SELECT id,display_name,category,amount,minimum_amount,currency_code,status,"
            + "target_id,destination_account_id FROM transactions"
            + " WHERE id=? AND record_kind='BILL' AND user_id=?"
            + " AND (source_account_id IS NULL OR source_account_id IN"
            + " (SELECT a.id FROM accounts a JOIN customers c ON c.id=a.customer_id WHERE c.user_id=?))"
            + (lock ? " FOR UPDATE" : ""),
        (r, n) -> new Bill(r.getString(1), r.getString(2), r.getString(3), r.getBigDecimal(4),
            r.getBigDecimal(5), r.getString(6), r.getString(7), r.getString(8), r.getObject(9, Long.class)), id, owner, owner);
    if (rows.isEmpty()) throw new ResourceNotFoundException("This bill could not be found.");
    return rows.get(0);
  }

  private Attempt find(String id, String owner, boolean lock) {
    var rows = attempts("id=? AND user_id=?", lock, id, owner);
    if (rows.isEmpty()) throw new ResourceNotFoundException("This bill payment review could not be found.");
    return rows.get(0);
  }

  private List<Attempt> attempts(String where, boolean lock, Object... args) {
    return db.query("SELECT * FROM bill_payment_attempts WHERE " + where + (lock ? " FOR UPDATE" : ""),
        (r, n) -> read(r), args);
  }

  private static Attempt read(ResultSet r) throws SQLException {
    Timestamp completed = r.getTimestamp("completed_at");
    return new Attempt(new Receipt(r.getString("id"), r.getString("bill_id"), r.getString("biller_name"),
        Long.toString(r.getLong("source_account_id")), r.getString("source_name"), r.getString("source_masked"),
        r.getString("payee_id"), r.getString("recipient_name"), r.getString("destination_masked"),
        r.getBigDecimal("amount").setScale(2).toPlainString(), r.getString("currency_code"), r.getString("status"),
        r.getString("transaction_reference"), r.getTimestamp("expires_at").toInstant().toString(),
        completed == null ? null : completed.toInstant().toString(), r.getString("failure_reason")),
        r.getLong("destination_account_id"), r.getBigDecimal("original_remaining"), r.getString("request_fingerprint"));
  }

  private Receipt expire(Attempt attempt, String owner) {
    Receipt receipt = attempt.receipt();
    if (expired(receipt)) return terminal(attempt, owner, "EXPIRED", "This review expired. Start a new review before paying.");
    return receipt;
  }

  private boolean expired(Receipt receipt) {
    return "READY".equals(receipt.status()) && !clock.instant().isBefore(Instant.parse(receipt.expiresAt()));
  }

  private Receipt visible(Receipt receipt) {
    if (!expired(receipt)) return receipt;
    return new Receipt(receipt.id(), receipt.billId(), receipt.billerName(), receipt.sourceAccountId(),
        receipt.sourceName(), receipt.sourceMasked(), receipt.payeeId(), receipt.recipientName(),
        receipt.destinationMasked(), receipt.amount(), receipt.currencyCode(), "EXPIRED", null,
        receipt.expiresAt(), null, "This review expired. Start a new review before paying.");
  }

  private Receipt terminal(Attempt attempt, String owner, String status, String reason) {
    requireOne(db.update("UPDATE bill_payment_attempts SET status=?,failure_reason=?,updated_at=?"
            + " WHERE id=? AND user_id=? AND status='READY'", status, reason, Timestamp.from(clock.instant()),
        attempt.receipt().id(), owner));
    return find(attempt.receipt().id(), owner, false).receipt();
  }

  private static String fingerprint(PrepareRequest request) {
    return sha256(part(request.billId()) + part(Long.toString(Long.parseLong(request.sourceAccountId())))
        + part(request.payeeId()) + part(request.amount() == null ? null : request.amount().stripTrailingZeros().toPlainString()));
  }

  private static String part(String value) { return value == null ? "-1:" : value.length() + ":" + value; }
  private static String sha256(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }
  private static String mask(Account account) {
    String number = account.getAccountNumber();
    return "•••• " + number.substring(Math.max(0, number.length() - 4));
  }
  private static void requireOne(int count) {
    if (count != 1) throw new IllegalStateException("A bill payment write did not affect exactly one record.");
  }
}
