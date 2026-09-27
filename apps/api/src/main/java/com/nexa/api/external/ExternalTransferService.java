package com.nexa.api.external;

import com.nexa.api.beans.Account;
import com.nexa.api.beans.AccountCategory;
import com.nexa.api.beans.AccountStatus;
import com.nexa.api.beans.AccountType;
import com.nexa.api.exep.ConflictException;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import com.nexa.api.repository.AccountDao;
import com.nexa.api.service.CurrentUserProvider;
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
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Provider sandbox requests only. There are deliberately no ledger, balance, bill or transaction
 * service mutations here. A committed submission claim prevents duplicate outbound POST requests.
 */
@Service
public class ExternalTransferService {
  public record PrepareRequest(@NotNull UUID requestKey,
      @NotBlank @Pattern(regexp = "[0-9]{1,19}") String sourceAccountId,
      @NotBlank @Size(max = 40) String payeeId,
      @NotNull @DecimalMin("1.00") @Digits(integer = 13, fraction = 2) BigDecimal amount) {}
  public record Readiness(boolean ready, String environment, String provider, String reason) {}
  public record Receipt(String id, String environment, String provider, String sourceAccountId,
      String sourceName, String sourceMasked, String payeeId, String recipientName, String bankName,
      String destinationMasked, String ifsc, String amount, String currencyCode, String status,
      String providerTransferId, String providerStatus, String statusCode, String utr, String expiresAt,
      String completedAt, String failureReason, String updatedAt) {}
  private record Stored(Receipt receipt, String requestFingerprint, String payeeFingerprint, String providerRequestId) {}
  private record Claim(Receipt receipt, ExternalPayeeService.Payee payee,
      ExternalPayoutProvider.PayoutRequest request) {}
  private static final String PROVIDER = "CASHFREE";
  private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");
  private static final String UNCERTAIN = "The sandbox provider has not confirmed the result. Refresh this transfer; do not submit a replacement.";

  private final JdbcTemplate db;
  private final CurrentUserProvider user;
  private final AccountDao accounts;
  private final EntityManager entities;
  private final ExternalPayeeService payees;
  private final ExternalAccountCipher accountCipher;
  private final ExternalPayoutProvider provider;
  private final Clock clock;
  private final TransactionTemplate tx;

  public ExternalTransferService(JdbcTemplate db, CurrentUserProvider user, AccountDao accounts,
      EntityManager entities, ExternalPayeeService payees, ExternalAccountCipher accountCipher, ExternalPayoutProvider provider,
      Clock clock, PlatformTransactionManager transactionManager) {
    this.db = db; this.user = user; this.accounts = accounts; this.entities = entities;
    this.payees = payees; this.accountCipher = accountCipher; this.provider = provider; this.clock = clock;
    tx = new TransactionTemplate(transactionManager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public Readiness readiness() {
    String environment = provider.environment().name();
    boolean providerReady = provider.environment() == ExternalPayoutProvider.Environment.SANDBOX && provider.available();
    boolean ready = providerReady && accountCipher.available();
    String reason = ready ? "Sandbox only. No Nexa balances or real bank funds are moved."
        : provider.environment() == ExternalPayoutProvider.Environment.LIVE
            ? "Live external transfers are unavailable. Configure the sandbox environment first."
            : providerReady && !accountCipher.available()
                ? "Secure external-payee storage is not configured. Ask the administrator to configure the account encryption key."
                : provider.unavailableReason();
    if (reason == null || reason.isBlank()) reason = "External transfer sandbox is not configured.";
    return new Readiness(ready, environment, PROVIDER, reason);
  }

  public Receipt prepare(PrepareRequest request) {
    validateRequest(request);
    String owner = user.userId();
    return tx.execute(ignored -> {
      String id = "XT-" + request.requestKey();
      String fingerprint = fingerprint(request);
      List<Stored> existing = query("id=? AND user_id=?", true, id, owner);
      if (!existing.isEmpty()) {
        if (!fingerprint.equals(existing.get(0).requestFingerprint()))
          throw new ConflictException("This request key was already used for different transfer details.");
        return expire(existing.get(0), owner);
      }
      requireSandbox();
      // The owner lock serializes request-key creation across accounts and payees.
      db.queryForObject("SELECT user_id FROM customers WHERE user_id=? FOR UPDATE", String.class, owner);
      existing = query("id=? AND user_id=?", true, id, owner);
      if (!existing.isEmpty()) {
        if (!fingerprint.equals(existing.get(0).requestFingerprint()))
          throw new ConflictException("This request key was already used for different transfer details.");
        return expire(existing.get(0), owner);
      }
      if (db.queryForObject("SELECT COUNT(*) FROM external_transfer_reviews WHERE id=?", Long.class, id) > 0)
        throw new ConflictException("This request key is already in use. Start a new review.");
      ExternalPayeeService.Payee payee = payees.requireOwned(request.payeeId(), owner, true);
      requireActivePayee(payee);
      Account source = source(Long.parseLong(request.sourceAccountId()), owner, request.amount());
      Instant now = clock.instant();
      db.update("INSERT INTO external_transfer_reviews(id,request_key,request_fingerprint,provider_request_id,user_id,environment,provider,"
              + "source_account_id,source_name,source_masked,payee_id,payee_fingerprint,recipient_name,bank_name,"
              + "destination_masked,ifsc,amount,currency_code,status,created_at,updated_at,expires_at)"
              + " VALUES(?,?,?,?,?,'SANDBOX',?,?,?,?,?,?,?,?,?,?,?,'INR','READY',?,?,?)",
          id, request.requestKey().toString(), fingerprint, "XT_" + request.requestKey().toString().replace("-", ""), owner, PROVIDER, source.getId(),
          source.getAccountName(), mask(source), payee.id(), payee.fingerprint(), payee.recipientName(),
          payee.bankName(), payee.accountNumberMasked(), payee.ifsc(), request.amount(),
          Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.plusSeconds(300)));
      return find(id, owner, false).receipt();
    });
  }

  public Receipt status(String id) {
    String owner = user.userId();
    return tx.execute(ignored -> expire(find(id, owner, true), owner));
  }

  public List<Receipt> list(String payeeId) {
    String owner = user.userId();
    return tx.execute(ignored -> {
      if (payeeId != null) payees.requireOwned(payeeId, owner, false);
      return query("user_id=? AND (? IS NULL OR payee_id=?) ORDER BY created_at DESC,id DESC FETCH NEXT 100 ROWS ONLY",
          false, owner, payeeId, payeeId).stream().map(s -> visible(s.receipt())).toList();
    });
  }

  public Receipt cancel(String id) {
    String owner = user.userId();
    return tx.execute(ignored -> {
      Receipt receipt = expire(find(id, owner, true), owner);
      if (!"READY".equals(receipt.status())) return receipt;
      return terminal(id, owner, "CANCELLED", "You cancelled this sandbox transfer review.");
    });
  }

  public Receipt confirm(String id) {
    rejectAmbientTransaction();
    String owner = user.userId();
    Claim claim = tx.execute(ignored -> {
      Stored stored = find(id, owner, true);
      Receipt receipt = expire(stored, owner);
      if (!"READY".equals(receipt.status())) return new Claim(receipt, null, null);
      requireSandbox();
      ExternalPayeeService.Payee payee;
      try {
        payee = payees.requireOwned(receipt.payeeId(), owner, true);
        requireActivePayee(payee);
        if (!stored.payeeFingerprint().equals(payee.fingerprint()))
          throw new InvalidRequestException("The payee's bank details changed. Start a new sandbox review.");
        if (!receipt.recipientName().equals(payee.recipientName()))
          throw new InvalidRequestException("The payee's recipient name changed. Start a new sandbox review.");
        source(Long.parseLong(receipt.sourceAccountId()), owner, new BigDecimal(receipt.amount()));
      } catch (InvalidRequestException | ResourceNotFoundException failure) {
        return new Claim(terminal(id, owner, "FAILED", safePreflightReason(failure)), null, null);
      }
      requireOne(db.update("UPDATE external_transfer_reviews SET status='SUBMITTING',updated_at=?"
          + " WHERE id=? AND user_id=? AND status='READY'", Timestamp.from(clock.instant()), id, owner));
      return new Claim(find(id, owner, false).receipt(), payee,
          new ExternalPayoutProvider.PayoutRequest(stored.providerRequestId(), payee.accountNumber(), payee.ifsc(),
              receipt.recipientName(), new BigDecimal(receipt.amount())));
    });
    if (claim.request() == null) return claim.receipt();
    // REQUIRES_NEW above has committed before any HTTP call. Duplicate confirmations never POST.
    ExternalPayoutProvider.Outcome outcome;
    try { outcome = provider.submit(claim.request()); }
    catch (RuntimeException uncertain) { outcome = null; }
    return recordOutcome(id, owner, claim.payee(), outcome, false);
  }

  public Receipt refresh(String id) {
    rejectAmbientTransaction();
    String owner = user.userId();
    Claim claim = tx.execute(ignored -> {
      Stored stored = find(id, owner, true);
      Receipt receipt = expire(stored, owner);
      if (!List.of("SUBMITTING", "PENDING", "COMPLETED").contains(receipt.status()))
        return new Claim(receipt, null, null);
      requireSandbox();
      ExternalPayeeService.Payee payee = payees.requireOwned(receipt.payeeId(), owner, false);
      if (!stored.payeeFingerprint().equals(payee.fingerprint()))
        return new Claim(receipt, null, null);
      return new Claim(receipt, payee, new ExternalPayoutProvider.PayoutRequest(stored.providerRequestId(),
          payee.accountNumber(), payee.ifsc(), receipt.recipientName(), new BigDecimal(receipt.amount())));
    });
    if (claim.payee() == null) return claim.receipt();
    ExternalPayoutProvider.Outcome outcome;
    try { outcome = provider.status(claim.request().transferId()); }
    catch (RuntimeException uncertain) { outcome = null; }
    return recordOutcome(id, owner, claim.payee(), outcome, true);
  }

  private Receipt recordOutcome(String id, String owner, ExternalPayeeService.Payee payee,
      ExternalPayoutProvider.Outcome outcome, boolean authenticatedLookup) {
    return tx.execute(ignored -> {
      Stored stored = find(id, owner, true);
      Receipt receipt = stored.receipt();
      if (!List.of("SUBMITTING", "PENDING", "COMPLETED").contains(receipt.status())) return receipt;
      boolean matchesPresentDetails = outcome != null && stored.providerRequestId().equals(outcome.transferId())
          && (outcome.amount() == null || new BigDecimal(receipt.amount()).compareTo(outcome.amount()) == 0)
          && (outcome.bankAccountNumber() == null || payee.accountNumber().equals(outcome.bankAccountNumber()))
          && (outcome.ifsc() == null || payee.ifsc().equals(outcome.ifsc()))
          && (outcome.providerTransferId() == null || safeIdentifier(outcome.providerTransferId(), 100) != null)
          && (receipt.providerTransferId() == null || outcome.providerTransferId() == null
              || receipt.providerTransferId().equals(outcome.providerTransferId()));
      boolean correlated = matchesPresentDetails && (authenticatedLookup
          || (outcome.amount() != null && outcome.bankAccountNumber() != null && outcome.ifsc() != null));
      String next = "PENDING";
      String reason = UNCERTAIN;
      if (correlated) {
        if (outcome.state() == ExternalPayoutProvider.State.SUCCESS
            && "SUCCESS".equals(outcome.providerStatus()) && "COMPLETED".equals(outcome.statusCode())) {
          next = "COMPLETED"; reason = null;
        } else if (outcome.state() == ExternalPayoutProvider.State.FAILED) {
          next = "FAILED"; reason = "The sandbox provider rejected this transfer. No Nexa money was moved.";
        } else if (outcome.state() == ExternalPayoutProvider.State.REVERSED) {
          next = "REVERSED"; reason = "The sandbox provider reports this transfer was reversed. No Nexa money was moved.";
        }
      }
      // A delayed response cannot overwrite established completion; a matched reversal can.
      if ("COMPLETED".equals(receipt.status()) && !"REVERSED".equals(next)) return receipt;
      Instant now = clock.instant();
      Timestamp completed = "COMPLETED".equals(next) ? Timestamp.from(now)
          : receipt.completedAt() == null ? null : Timestamp.from(Instant.parse(receipt.completedAt()));
      requireOne(db.update("UPDATE external_transfer_reviews SET status=?,provider_transfer_id=?,provider_status=?,"
              + "status_code=?,utr=?,failure_reason=?,completed_at=?,updated_at=? WHERE id=? AND user_id=?",
          next, matchesPresentDetails && outcome.providerTransferId() != null
              ? safeIdentifier(outcome.providerTransferId(), 100) : receipt.providerTransferId(),
          matchesPresentDetails ? safeCode(outcome.providerStatus()) : receipt.providerStatus(),
          matchesPresentDetails ? safeCode(outcome.statusCode()) : receipt.statusCode(),
          matchesPresentDetails ? safeIdentifier(outcome.utr(), 80) : receipt.utr(), reason, completed,
          Timestamp.from(now), id, owner));
      return find(id, owner, false).receipt();
    });
  }

  private Account source(long id, String owner, BigDecimal amount) {
    Account account = accounts.findLockedById(id)
        .orElseThrow(() -> new InvalidRequestException("Choose one of your active Nexa deposit accounts."));
    entities.refresh(account);
    if (account.getCustomer() == null || !owner.equals(account.getCustomer().getUserId())
        || account.getAccountCategory() != AccountCategory.CUSTOMER
        || (account.getAccountType() != AccountType.SAVINGS && account.getAccountType() != AccountType.CURRENT)
        || account.getStatus() != AccountStatus.ACTIVE || !"INR".equals(account.getCurrencyCode()))
      throw new InvalidRequestException("Choose one of your active Nexa INR deposit accounts.");
    if (account.getBalance().compareTo(amount) < 0)
      throw new InvalidRequestException("The review amount exceeds this account's available balance. Choose a smaller amount.");
    return account;
  }

  private void requireSandbox() {
    Readiness readiness = readiness();
    if (!readiness.ready()) throw new InvalidRequestException(readiness.reason());
  }
  private static void requireActivePayee(ExternalPayeeService.Payee payee) {
    if (!"ACTIVE".equals(payee.status())) throw new InvalidRequestException("Choose an active saved external-bank payee.");
    if (payee.accountNumber() == null || !payee.accountNumber().matches("[A-Za-z0-9]{9,18}")
        || payee.ifsc() == null || !payee.ifsc().matches("[A-Z]{4}0[A-Z0-9]{6}")
        || payee.recipientName() == null || payee.recipientName().isBlank()
        || payee.recipientName().codePointCount(0, payee.recipientName().length()) > 100
        || !payee.recipientName().matches("[\\p{L}\\p{M} ]+"))
      throw new InvalidRequestException("Save a valid bank account, IFSC and recipient name using letters and spaces before reviewing a sandbox transfer.");
  }
  private static String safePreflightReason(RuntimeException failure) {
    return failure instanceof ResourceNotFoundException ? "The saved payee is no longer available. Start a new sandbox review."
        : failure.getMessage(); // Only local validation messages above, never provider messages.
  }
  private static void rejectAmbientTransaction() {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("External sandbox submission must run outside an enclosing database transaction.");
  }

  private Stored find(String id, String owner, boolean lock) {
    var rows = query("id=? AND user_id=?", lock, id, owner);
    if (rows.isEmpty()) throw new ResourceNotFoundException("This external transfer review could not be found.");
    return rows.get(0);
  }
  private List<Stored> query(String where, boolean lock, Object... args) {
    return db.query("SELECT * FROM external_transfer_reviews WHERE " + where + (lock ? " FOR UPDATE" : ""),
        (r, n) -> read(r), args);
  }
  private static Stored read(ResultSet r) throws SQLException {
    Timestamp completed = r.getTimestamp("completed_at");
    return new Stored(new Receipt(r.getString("id"), r.getString("environment"), r.getString("provider"),
        Long.toString(r.getLong("source_account_id")), r.getString("source_name"), r.getString("source_masked"),
        r.getString("payee_id"), r.getString("recipient_name"), r.getString("bank_name"),
        r.getString("destination_masked"), r.getString("ifsc"), r.getBigDecimal("amount").setScale(2).toPlainString(),
        r.getString("currency_code"), r.getString("status"), r.getString("provider_transfer_id"),
        r.getString("provider_status"), r.getString("status_code"), r.getString("utr"),
        r.getTimestamp("expires_at").toInstant().toString(), completed == null ? null : completed.toInstant().toString(),
        r.getString("failure_reason"), r.getTimestamp("updated_at").toInstant().toString()),
        r.getString("request_fingerprint"), r.getString("payee_fingerprint"), r.getString("provider_request_id"));
  }
  private Receipt expire(Stored stored, String owner) {
    return expired(stored.receipt()) ? terminal(stored.receipt().id(), owner, "EXPIRED", "This sandbox review expired. Start a new review.")
        : stored.receipt();
  }
  private Receipt visible(Receipt receipt) {
    if (!expired(receipt)) return receipt;
    return new Receipt(receipt.id(), receipt.environment(), receipt.provider(), receipt.sourceAccountId(),
        receipt.sourceName(), receipt.sourceMasked(), receipt.payeeId(), receipt.recipientName(), receipt.bankName(),
        receipt.destinationMasked(), receipt.ifsc(), receipt.amount(), receipt.currencyCode(), "EXPIRED", null,
        null, null, null, receipt.expiresAt(), null, "This sandbox review expired. Start a new review.", receipt.updatedAt());
  }
  private boolean expired(Receipt receipt) {
    return "READY".equals(receipt.status()) && !clock.instant().isBefore(Instant.parse(receipt.expiresAt()));
  }
  private Receipt terminal(String id, String owner, String status, String reason) {
    requireOne(db.update("UPDATE external_transfer_reviews SET status=?,failure_reason=?,updated_at=?"
        + " WHERE id=? AND user_id=? AND status='READY'", status, reason, Timestamp.from(clock.instant()), id, owner));
    return find(id, owner, false).receipt();
  }
  private static void validateRequest(PrepareRequest request) {
    if (request == null || request.requestKey() == null || request.sourceAccountId() == null
        || !request.sourceAccountId().matches("[0-9]{1,19}") || request.payeeId() == null
        || request.payeeId().isBlank() || request.payeeId().length() > 40 || request.amount() == null
        || request.amount().compareTo(BigDecimal.ONE) < 0 || request.amount().scale() > 2
        || request.amount().compareTo(MAX_AMOUNT) > 0)
      throw new InvalidRequestException("Choose a source account, an external payee and an amount of at least INR 1 with up to two decimal places.");
    try { if (Long.parseLong(request.sourceAccountId()) <= 0) throw new NumberFormatException(); }
    catch (NumberFormatException invalid) { throw new InvalidRequestException("Choose a valid source account."); }
  }
  private static String fingerprint(PrepareRequest request) {
    return sha256(part(Long.toString(Long.parseLong(request.sourceAccountId()))) + part(request.payeeId())
        + part(request.amount().stripTrailingZeros().toPlainString()));
  }
  private static String part(String value) { return value.length() + ":" + value; }
  private static String sha256(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }
  private static String safeCode(String value) { return value != null && value.matches("[A-Z0-9_]{1,64}") ? value : null; }
  private static String safeIdentifier(String value, int max) {
    return value != null && value.length() <= max && value.matches("[A-Za-z0-9_-]+") ? value : null;
  }
  private static String mask(Account account) {
    String value = account.getAccountNumber(); return "•••• " + value.substring(Math.max(0, value.length() - 4));
  }
  private static void requireOne(int count) {
    if (count != 1) throw new IllegalStateException("External transfer state was not written exactly once.");
  }
}
