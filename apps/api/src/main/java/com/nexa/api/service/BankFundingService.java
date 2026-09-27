package com.nexa.api.service;

import com.nexa.api.beans.Account;
import com.nexa.api.beans.AccountCategory;
import com.nexa.api.beans.AccountStatus;
import com.nexa.api.beans.AccountType;
import com.nexa.api.exep.ConflictException;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import com.nexa.api.repository.AccountDao;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records an administrator's acknowledgment of received cash, not an external transfer or verification. */
@Service
public class BankFundingService {
  private static final String CASH = "SYSTEM-CASH", RESERVE = "NEXA-BANK-FUNDING";
  private static final BigDecimal MAX_RECEIPT = new BigDecimal("10000000.00");
  private static final BigDecimal MAX_BALANCE = new BigDecimal("99999999999999999.99");
  private final JdbcTemplate db;
  private final CurrentUserProvider current;
  private final AccountDao accounts;
  private final EntityManager entities;
  private final Clock clock;

  public BankFundingService(JdbcTemplate db, CurrentUserProvider current, AccountDao accounts,
      EntityManager entities, Clock clock) {
    this.db = db; this.current = current; this.accounts = accounts; this.entities = entities; this.clock = clock;
  }

  public record Request(UUID requestId, BigDecimal amount, String source, String reference,
      String reason, boolean confirmed) {}
  public record Receipt(String id, String requestId, String receiptNumber, String source, String reference,
      String reason, String amount, String currencyCode, String transactionId, String recordedBy,
      String recordedAt, String reserveBalanceAfter, String cashBalanceAfter) {}
  public record Overview(String reserveBalance, String cashBalance, String currencyCode,
      boolean ready, String reason, List<Receipt> receipts) {}
  private record Actor(String id, String name) {}
  private record Payload(UUID requestId, BigDecimal amount, String source, String reference, String reason) {}
  private record Stored(Receipt receipt, String fingerprint) {}
  private record Infrastructure(Account cash, Account reserve) {}

  @Transactional(readOnly = true)
  public Overview overview() {
    authorize(false);
    Account cash = accounts.findByAccountNumber(CASH).orElse(null);
    Account reserve = accounts.findByAccountNumber(RESERVE).orElse(null);
    String reason = null;
    try { validateInfrastructure(cash, reserve); }
    catch (InvalidRequestException unavailable) { reason = unavailable.getMessage(); }
    var receipts = db.query("SELECT * FROM bank_funding_receipts ORDER BY recorded_at DESC,id DESC FETCH FIRST 20 ROWS ONLY",
        (rs, row) -> stored(rs).receipt());
    return new Overview(displayBalance(reserve), displayBalance(cash), "INR", reason == null, reason, receipts);
  }

  @Transactional(readOnly = true)
  public Receipt byRequest(UUID requestId) {
    authorize(false);
    return find(requestId).orElseThrow(() -> new ResourceNotFoundException("Bank funding receipt not found.")).receipt();
  }

  @Transactional
  public Receipt record(Request request) {
    Actor actor = authorize(true);
    Payload payload = normalize(request);
    String fingerprint = fingerprint(payload);
    var prior = find(payload.requestId());
    if (prior.isPresent()) return replay(prior.get(), fingerprint);
    Infrastructure infrastructure = lockInfrastructure();
    // Every capital receipt locks the same account pair. Recheck after those locks to serialize
    // retries and duplicate references across different administrators, without a race to insert.
    prior = find(payload.requestId());
    if (prior.isPresent()) return replay(prior.get(), fingerprint);
    if (db.queryForObject("SELECT COUNT(*) FROM bank_funding_receipts WHERE reference_key=?", Long.class,
        payload.reference()) != 0L)
      throw new ConflictException("This cash receipt reference has already been recorded. Check the existing receipt.");
    Account cash = infrastructure.cash(), reserve = infrastructure.reserve();
    BigDecimal cashAfter = cash.getBalance().add(payload.amount()), reserveAfter = reserve.getBalance().add(payload.amount());
    requireCapacity(cashAfter); requireCapacity(reserveAfter);
    Instant now = clock.instant();
    Timestamp at = Timestamp.from(now);
    String id = "BF-" + payload.requestId(), transaction = "TX-" + UUID.randomUUID();
    String receiptNumber = "BC-" + DateTimeFormatter.BASIC_ISO_DATE.format(now.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate())
        + "-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);

    cash.setBalance(cashAfter); reserve.setBalance(reserveAfter); entities.flush();
    db.update("INSERT INTO transactions(id,record_kind,transaction_type,destination_account_id,amount,currency_code,status,"
        + "operation,transaction_reference,target_id,user_id,merchant_name,category,payment_method,created_at,completed_at)"
        + " VALUES(?,'PAYMENT','DEPOSIT',?,?,'INR','SUCCESS','BANK_CAPITAL_RECEIPT',?,?,?,'Bank capital','BANK_FUNDING','CASH',?,?)",
        transaction, reserve.getId(), payload.amount(), transaction, id, actor.id(), at, at);
    db.update("INSERT INTO journal_entries(transaction_id,entry_reference,entry_type,status,created_at)"
        + " VALUES(?,?,'TRANSACTION','POSTED',?)", transaction, "J" + transaction, at);
    ledger(transaction, cash.getId(), "DEBIT", payload.amount(), at);
    ledger(transaction, reserve.getId(), "CREDIT", payload.amount(), at);
    db.update("INSERT INTO transactions(id,record_kind,user_id,source_account_id,target_id,operation,status,amount,currency_code,"
        + "transaction_reference,audit_reason,created_at) VALUES(?,'ADMIN_EVENT',?,?,?,'BANK_CAPITAL_RECEIPT','COMPLETED',?,'INR',?,?,?)",
        "A-" + UUID.randomUUID(), actor.id(), reserve.getId(), id, payload.amount(), transaction, payload.reason(), at);
    db.update("INSERT INTO bank_funding_receipts(id,request_id,request_fingerprint,receipt_number,source,reference_key,reason,amount,"
        + "currency_code,transaction_id,cash_account_id,reserve_account_id,recorded_by,recorded_by_name,recorded_at,confirmation_version,"
        + "reserve_balance_after,cash_balance_after) VALUES(?,?,?,?,?,?,?,?,'INR',?,?,?,?,?,?,'cash-capital-v1',?,?)",
        id, payload.requestId().toString(), fingerprint, receiptNumber, payload.source(), payload.reference(), payload.reason(), payload.amount(),
        transaction, cash.getId(), reserve.getId(), actor.id(), actor.name(), at, reserveAfter, cashAfter);
    return find(payload.requestId()).orElseThrow().receipt();
  }

  private Actor authorize(boolean lock) {
    String id = current.userId();
    var actors = db.query("SELECT full_name,role,status FROM customers WHERE user_id=?" + (lock ? " FOR UPDATE" : ""),
        (rs, row) -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3)}, id);
    if (actors.size() != 1 || !"ADMIN".equals(actors.get(0)[1]) || !"ACTIVE".equals(actors.get(0)[2]))
      throw new AccessDeniedException("Active administrator access is required to record bank capital.");
    return new Actor(id, actors.get(0)[0]);
  }

  private Infrastructure lockInfrastructure() {
    long cashId = systemId(CASH), reserveId = systemId(RESERVE);
    Account low = lock(Math.min(cashId, reserveId)), high = lock(Math.max(cashId, reserveId));
    Account lockedCash = low.getId() == cashId ? low : high;
    Account lockedReserve = low.getId() == reserveId ? low : high;
    validateInfrastructure(lockedCash, lockedReserve);
    return new Infrastructure(lockedCash, lockedReserve);
  }

  private long systemId(String number) {
    var ids = db.queryForList("SELECT id FROM accounts WHERE account_number=?", Long.class, number);
    if (ids.size() != 1) throw new InvalidRequestException("The bank cash or funding reserve account is unavailable.");
    return ids.get(0);
  }

  private Account lock(long id) {
    // Acquire the row lock before materializing an entity/version. Promoting a preloaded
    // entity to a Hibernate pessimistic lock can reject a valid concurrent receipt after
    // the prior writer commits; refreshing under this shared JDBC/JPA transaction is safe.
    if (db.queryForList("SELECT id FROM accounts WHERE id=? FOR UPDATE", Long.class, id).size() != 1)
      throw new InvalidRequestException("A bank funding account is unavailable.");
    Account account = accounts.findById(id).orElseThrow(() -> new InvalidRequestException("A bank funding account is unavailable."));
    entities.refresh(account);
    return account;
  }

  private static void validateInfrastructure(Account cash, Account reserve) {
    requireAccount(cash, CASH, AccountType.CASH); requireAccount(reserve, RESERVE, AccountType.CLEARING);
    if (cash.getId().equals(reserve.getId())) throw new InvalidRequestException("Bank cash and reserve accounts must be distinct.");
    if (cash.getBalance().signum() < 0) throw new InvalidRequestException("The bank cash balance needs reconciliation before recording capital.");
    // V21 can leave the reserve negative when historical loans predate recorded bank capital.
    // An actual receipt may reduce that deficit; it must not fabricate an opening balance.
  }

  private static void requireAccount(Account account, String number, AccountType type) {
    if (account == null || !number.equals(account.getAccountNumber()) || account.getAccountCategory() != AccountCategory.SYSTEM
        || account.getAccountType() != type || account.getCustomer() != null || account.getStatus() != AccountStatus.ACTIVE
        || !"INR".equals(account.getCurrencyCode()) || account.getBalance() == null || account.getBalance().scale() > 2)
      throw new InvalidRequestException("The bank cash or funding reserve account is unavailable or incorrectly configured.");
    requireCapacity(account.getBalance());
  }

  private static void requireCapacity(BigDecimal amount) {
    if (amount.abs().compareTo(MAX_BALANCE) > 0)
      throw new InvalidRequestException("This receipt exceeds supported bank account balance capacity.");
  }

  private static Payload normalize(Request request) {
    if (request == null || request.requestId() == null || !request.confirmed())
      throw new InvalidRequestException("Supply a request ID and explicitly confirm that this bank-capital cash was actually received.");
    BigDecimal amount = request.amount();
    if (amount == null || amount.scale() > 2 || amount.signum() <= 0 || amount.compareTo(MAX_RECEIPT) > 0)
      throw new InvalidRequestException("Enter a cash amount greater than zero and no more than INR 10000000, with at most two decimal places.");
    String source = text(request.source(), "capital source", 160), reason = text(request.reason(), "recording reason", 500);
    String reference = request.reference();
    if (reference == null || reference.length() > 1000) throw new InvalidRequestException("Enter a cash receipt reference of 1–80 letters, digits or / . _ - characters.");
    reference = reference.replaceAll("(?U)\\s+", "");
    if (!reference.matches("[A-Za-z0-9][A-Za-z0-9/._-]{0,79}"))
      throw new InvalidRequestException("Enter a cash receipt reference of 1–80 letters, digits or / . _ - characters.");
    reference = reference.toUpperCase(Locale.ROOT);
    return new Payload(request.requestId(), amount.setScale(2), source, reference, reason);
  }

  private static String text(String raw, String label, int maximumBytes) {
    if (raw == null || raw.length() > maximumBytes * 4) throw new InvalidRequestException("Enter a valid " + label + ".");
    String value = raw.replaceAll("(?U)\\s+", " ").strip();
    if (value.isEmpty() || value.getBytes(StandardCharsets.UTF_8).length > maximumBytes
        || value.codePoints().anyMatch(code -> Character.isISOControl(code) || Character.getType(code) == Character.FORMAT))
      throw new InvalidRequestException("The " + label + " must be 1–" + maximumBytes + " UTF-8 bytes without control characters.");
    return value;
  }

  private static String fingerprint(Payload payload) {
    try {
      String value = String.join("\u001F", payload.amount().toPlainString(), payload.source(), payload.reference(), payload.reason(), "cash-capital-v1");
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }

  private Optional<Stored> find(UUID requestId) {
    return db.query("SELECT * FROM bank_funding_receipts WHERE request_id=?", (rs, row) -> stored(rs), requestId.toString()).stream().findFirst();
  }

  private static Receipt replay(Stored stored, String fingerprint) {
    if (!stored.fingerprint().equals(fingerprint)) throw new ConflictException("This request ID was already used for different bank funding details.");
    return stored.receipt();
  }

  private static Stored stored(ResultSet rs) throws SQLException {
    return new Stored(new Receipt(rs.getString("id"), rs.getString("request_id"), rs.getString("receipt_number"), rs.getString("source"),
        rs.getString("reference_key"), rs.getString("reason"), money(rs.getBigDecimal("amount")), "INR", rs.getString("transaction_id"),
        rs.getString("recorded_by_name"), rs.getTimestamp("recorded_at").toInstant().toString(), money(rs.getBigDecimal("reserve_balance_after")),
        money(rs.getBigDecimal("cash_balance_after"))), rs.getString("request_fingerprint"));
  }

  private void ledger(String transaction, long account, String type, BigDecimal amount, Timestamp at) {
    if (db.update("INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at)"
        + " SELECT id,?,?,?,? FROM journal_entries WHERE transaction_id=?", account, type, amount, at, transaction) != 1)
      throw new IllegalStateException("Bank capital ledger entry could not be recorded.");
  }

  private static String money(BigDecimal amount) { return amount.setScale(2).toPlainString(); }
  private static String displayBalance(Account account) {
    return account == null || account.getBalance() == null || account.getBalance().scale() > 2 ? null : money(account.getBalance());
  }
}
