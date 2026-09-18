package com.nexa.api.service;

import com.nexa.api.beans.*;
import com.nexa.api.exep.*;
import com.nexa.api.repository.AccountDao;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminAccountService {
  private final JdbcTemplate db;
  private final AccountDao accounts;
  private final CurrentUserProvider user;
  private final TransactionService transactions;
  private final EntityManager em;

  public AdminAccountService(
      JdbcTemplate db,
      AccountDao accounts,
      CurrentUserProvider user,
      TransactionService transactions,
      EntityManager em) {
    this.db = db;
    this.accounts = accounts;
    this.user = user;
    this.transactions = transactions;
    this.em = em;
  }

  public record Edit(String name, AccountStatus status, Long version, String reason) {}

  public record Adjustment(String direction, BigDecimal amount, String reason, String requestId) {}

  public record View(
      long id,
      String number,
      String name,
      String type,
      String category,
      String status,
      String currency,
      BigDecimal balance,
      Long version,
      String customerName,
      String customerEmail) {}

  private void authorize(boolean lock) {
    var roles =
        db.queryForList(
            "SELECT role FROM customers WHERE user_id=? AND status='ACTIVE'"
                + (lock ? " FOR UPDATE" : ""),
            String.class,
            user.userId());
    if (roles.isEmpty() || !"ADMIN".equals(roles.get(0)))
      throw new org.springframework.security.access.AccessDeniedException(
          "Administrator access required");
  }

  private View view(Account a) {
    return new View(
        a.getId(),
        a.getAccountNumber(),
        a.getAccountName(),
        a.getAccountType().name(),
        a.getAccountCategory().name(),
        a.getStatus().name(),
        a.getCurrencyCode(),
        a.getBalance(),
        a.getVersion(),
        a.getCustomer() == null ? null : a.getCustomer().getFullName(),
        a.getCustomer() == null ? null : a.getCustomer().getEmail());
  }

  public List<View> list() {
    authorize(false);
    return accounts.findAll().stream().map(this::view).toList();
  }

  public record Workspace(
      View account,
      String createdAt,
      String updatedAt,
      Map<String, Object> terms,
      List<View> relatedAccounts,
      List<Map<String, Object>> mandates) {}

  private Account requireAccount(long id) {
    return accounts
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
  }

  public Workspace workspace(long id) {
    authorize(false);
    var a = requireAccount(id);
    var related =
        a.getCustomer() == null
            ? List.<View>of()
            : accounts.findByCustomerId(a.getCustomer().getId()).stream()
                .filter(other -> other.getId() != id)
                .map(this::view)
                .toList();
    var terms =
        db.queryForMap(
            "SELECT product_status,principal_amount,interest_rate,periodic_payment,"
                + "credit_limit,minimum_payment,funding_account_id,review_reason,reviewed_at,tenure_months FROM accounts WHERE id=?",
            id);
    var mandates =
        db.queryForList(
            "SELECT t.id,t.display_name,t.status,t.amount,t.currency_code,"
                + "t.effective_date,t.end_date,t.source_account_id,t.destination_account_id,s.account_name"
                + " AS source_name,d.account_name AS destination_name FROM transactions t LEFT JOIN"
                + " accounts s ON s.id=t.source_account_id LEFT JOIN accounts d ON"
                + " d.id=t.destination_account_id WHERE t.record_kind='MANDATE' AND"
                + " (t.source_account_id=? OR t.destination_account_id=?) ORDER BY t.created_at"
                + " DESC,t.id DESC",
            id,
            id);
    return new Workspace(
        view(a),
        Objects.toString(a.getCreatedAt(), null),
        Objects.toString(a.getUpdatedAt(), null),
        terms,
        related,
        mandates);
  }

  public record Activity(List<Map<String, Object>> items, long total, int page, int size) {}

  public Activity activity(long id, int page, int size) {
    authorize(false);
    requireAccount(id);
    if (page < 0 || size < 1 || size > 100)
      throw new InvalidRequestException("Supply a nonnegative page and a size of 1–100.");
    String scope =
        " FROM transactions t WHERE t.record_kind='PAYMENT' AND "
            + "(t.source_account_id=? OR t.destination_account_id=? OR EXISTS "
            + "(SELECT 1 FROM journal_entries j JOIN ledger_entries l ON l.journal_entry_id=j.id "
            + "WHERE j.transaction_id=t.id AND l.account_id=?))";
    long total = db.queryForObject("SELECT COUNT(*)" + scope, Long.class, id, id, id);
    var items =
        db.queryForList(
            "SELECT t.id,t.transaction_type,t.operation,t.amount,t.status,"
                + "t.source_account_id,t.destination_account_id,t.created_at"
                + scope
                + " ORDER BY t.created_at DESC,t.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            id,
            id,
            id,
            (long) page * size,
            size);
    return new Activity(items, total, page, size);
  }

  private void reason(String reason) {
    if (reason == null || reason.isBlank() || reason.length() > 500)
      throw new InvalidRequestException("Enter a reason of 1–500 characters.");
  }

  public View edit(long id, Edit r) {
    authorize(true);
    reason(r.reason());
    if (r.name() == null
        || r.name().isBlank()
        || r.name().length() > 120
        || r.status() == null
        || r.version() == null)
      throw new InvalidRequestException("Supply a name, status and current version.");
    var a =
        accounts
            .findLockedById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    em.refresh(a);
    if (!r.version().equals(a.getVersion()))
      throw new ConflictException("This account changed. Refresh before editing.");
    if (r.status() == AccountStatus.CLOSED && a.getBalance().signum() != 0)
      throw new InvalidRequestException("An account must have zero balance before closing.");
    if (a.getAccountType() == AccountType.LOAN
        && a.getStatus() == AccountStatus.CLOSED
        && r.status() != AccountStatus.CLOSED)
      throw new InvalidRequestException("A repaid loan cannot be reopened.");
    var beforeName = a.getAccountName();
    var beforeStatus = a.getStatus().name();
    a.setAccountName(r.name().trim());
    a.setStatus(r.status());
    em.flush();
    db.update(
        "INSERT INTO"
            + " transactions(id,record_kind,user_id,source_account_id,operation,status,audit_reason,before_name,after_name,before_status,after_status,created_at)"
            + " VALUES(?,'ADMIN_EVENT',?,?,'UPDATE_ACCOUNT','COMPLETED',?,?,?,?,?,CURRENT_TIMESTAMP)",
        "A-" + UUID.randomUUID(),
        user.userId(),
        id,
        r.reason().trim(),
        beforeName,
        a.getAccountName(),
        beforeStatus,
        a.getStatus().name());
    return view(a);
  }

  public Map<String, String> adjust(long id, Adjustment r) {
    authorize(true);
    reason(r.reason());
    String auditId;
    try {
      auditId = "A-" + UUID.fromString(r.requestId());
    } catch (Exception e) {
      throw new InvalidRequestException("Supply a UUID requestId.");
    }
    if (!Set.of("CREDIT", "DEBIT").contains(r.direction() == null ? "" : r.direction())
        || r.amount() == null
        || r.amount().signum() <= 0
        || r.amount().scale() > 2
        || r.amount().precision() - r.amount().scale() > 13)
      throw new InvalidRequestException(
          "Supply CREDIT or DEBIT and a positive amount with two decimals.");
    var prior = db.queryForList("SELECT * FROM transactions WHERE id=?", auditId);
    if (!prior.isEmpty()) {
      var p = prior.get(0);
      if (!user.userId().equals(p.get("USER_ID"))
          || ((Number) p.get("SOURCE_ACCOUNT_ID")).longValue() != id
          || !r.direction().equals(p.get("OPERATION"))
          || r.amount().compareTo((BigDecimal) p.get("AMOUNT")) != 0
          || !r.reason().trim().equals(p.get("AUDIT_REASON")))
        throw new ConflictException("Request ID already used with different details.");
      return Map.of("transactionId", p.get("TRANSACTION_REFERENCE").toString());
    }
    var a =
        accounts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    if (a.getAccountCategory() != AccountCategory.CUSTOMER
        || !Set.of(AccountType.SAVINGS, AccountType.CURRENT).contains(a.getAccountType()))
      throw new InvalidRequestException(
          "Adjust a deposit account. Loans use the loan disbursement and repayment workflow.");
    var request = new TransactionRequest();
    request.setAmount(r.amount());
    request.setSourceAccountId(id);
    request.setDestinationAccountId(id);
    var tx =
        "CREDIT".equals(r.direction())
            ? transactions.deposit(request)
            : transactions.withdraw(request);
    em.flush();
    db.update(
        "INSERT INTO"
            + " transactions(id,record_kind,user_id,source_account_id,operation,status,amount,transaction_reference,audit_reason,created_at)"
            + " VALUES(?,'ADMIN_EVENT',?,?,?,'COMPLETED',?,?,?,CURRENT_TIMESTAMP)",
        auditId,
        user.userId(),
        id,
        r.direction(),
        r.amount(),
        tx.getId(),
        r.reason().trim());
    return Map.of("transactionId", tx.getId());
  }

  public List<Map<String, Object>> history(long id) {
    authorize(false);
    requireAccount(id);
    return db.queryForList(
        "SELECT"
            + " t.id,t.operation,t.amount,t.audit_reason,t.before_name,t.after_name,t.before_status,t.after_status,t.transaction_reference,t.created_at,c.email"
            + " AS actor FROM transactions t JOIN customers c ON c.user_id=t.user_id WHERE"
            + " t.record_kind='ADMIN_EVENT' AND t.source_account_id=? ORDER BY t.created_at DESC"
            + " FETCH FIRST 100 ROWS ONLY",
        id);
  }
}
