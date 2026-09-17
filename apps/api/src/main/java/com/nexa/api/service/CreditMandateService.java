package com.nexa.api.service;

import com.nexa.api.exep.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mandate authorizations and events are transaction records; loans are debit-normal accounts. */
@Service
@Transactional
public class CreditMandateService {
  private final JdbcTemplate db;
  private final CurrentUserProvider user;

  public CreditMandateService(JdbcTemplate db, CurrentUserProvider user) {
    this.db = db;
    this.user = user;
  }

  public record MandateRequest(
      long sourceAccountId,
      Long beneficiaryAccountId,
      String beneficiaryAccountNumber,
      String payee,
      BigDecimal limit,
      LocalDate startDate,
      LocalDate endDate) {}

  public record LoanRequest(
      long accountId, String displayName, BigDecimal principal, BigDecimal interestRate) {}

  public record Execution(BigDecimal amount, String requestId) {}

  private record A(
      long id, String owner, String type, String status, String currency, BigDecimal balance) {}

  private A account(long id, boolean lock) {
    var rows =
        db.query(
            "SELECT a.*,c.user_id FROM accounts a LEFT JOIN customers c ON c.id=a.customer_id WHERE"
                + " a.id=?"
                + (lock ? " FOR UPDATE OF a.balance" : ""),
            (r, n) ->
                new A(
                    r.getLong("id"),
                    r.getString("user_id"),
                    r.getString("account_type"),
                    r.getString("status"),
                    r.getString("currency_code"),
                    r.getBigDecimal("balance")),
            id);
    if (rows.isEmpty()) throw new ResourceNotFoundException("Account not found");
    return rows.get(0);
  }

  private void owned(A a) {
    if (!user.userId().equals(a.owner)) throw new ResourceNotFoundException("Account not found");
  }

  private void depositAccount(A a) {
    if (!Set.of("SAVINGS", "CURRENT").contains(a.type)
        || !"ACTIVE".equals(a.status)
        || !"INR".equals(a.currency))
      throw new InvalidRequestException("An active INR deposit account is required");
  }

  private void money(BigDecimal value) {
    if (value == null
        || value.signum() <= 0
        || value.scale() > 2
        || value.precision() - value.scale() > 13)
      throw new InvalidRequestException("Use a positive amount with at most two decimal places");
  }

  private String id(String prefix) {
    return prefix + UUID.randomUUID().toString();
  }

  private void event(String parent, String state) {
    db.update(
        "INSERT INTO transactions(id,record_kind,user_id,parent_id,operation,status)"
            + " VALUES(?,'MANDATE_EVENT',?,?,?,?)",
        id("E-"),
        user.userId(),
        parent,
        state,
        state);
  }

  private long beneficiary(MandateRequest r) {
    if (r.beneficiaryAccountId != null && r.beneficiaryAccountNumber != null)
      throw new InvalidRequestException("Specify one beneficiary account");
    if (r.beneficiaryAccountId != null) return r.beneficiaryAccountId;
    var ids =
        db.queryForList(
            "SELECT id FROM accounts WHERE account_number=?",
            Long.class,
            r.beneficiaryAccountNumber);
    if (ids.isEmpty()) throw new InvalidRequestException("Beneficiary account not found");
    return ids.get(0);
  }

  public Map<String, Object> createMandate(MandateRequest r) {
    money(r.limit);
    A source = account(r.sourceAccountId, false), destination = account(beneficiary(r), false);
    owned(source);
    depositAccount(source);
    depositAccount(destination);
    if (source.id == destination.id
        || r.startDate == null
        || r.endDate != null && r.endDate.isBefore(r.startDate)
        || r.payee == null
        || r.payee.isBlank()
        || r.payee.length() > 160)
      throw new InvalidRequestException(
          "Specify distinct accounts, a payee and valid effective dates");
    String id = id("M-");
    db.update(
        "INSERT INTO"
            + " transactions(id,record_kind,user_id,source_account_id,destination_account_id,display_name,amount,currency_code,status,operation,effective_date,end_date,transaction_reference)"
            + " VALUES(?,'MANDATE',?,?,?,?,?,'INR','PENDING','TRANSFER',?,?,?)",
        id,
        user.userId(),
        source.id,
        destination.id,
        r.payee,
        r.limit,
        r.startDate.toString(),
        r.endDate == null ? null : r.endDate.toString(),
        id);
    event(id, "CREATED");
    return mandate(id, false);
  }

  public Map<String, Object> mandate(String id, boolean lock) {
    var rows =
        db.queryForList(
            "SELECT * FROM transactions WHERE record_kind='MANDATE' AND id=? AND user_id=?"
                + (lock ? " FOR UPDATE" : ""),
            id,
            user.userId());
    if (rows.isEmpty()) throw new ResourceNotFoundException("Mandate not found");
    return rows.get(0);
  }

  private String value(Map<String, Object> m, String key) {
    Object v = m.get(key);
    return v == null ? null : v.toString();
  }

  public Map<String, Object> activate(String id) {
    var m = mandate(id, true);
    String status = value(m, "STATUS");
    if ("ACTIVE".equals(status)) return m;
    if (!Set.of("PENDING", "PAUSED", "ACTION_REQUIRED").contains(status))
      throw new InvalidRequestException("This mandate cannot be activated");
    if (m.get("DESTINATION_ACCOUNT_ID") == null)
      throw new InvalidRequestException(
          "Legacy mandate has no verified beneficiary. Create a new authorization.");
    checkDates(m, false);
    depositAccount(account(((Number) m.get("SOURCE_ACCOUNT_ID")).longValue(), false));
    depositAccount(account(((Number) m.get("DESTINATION_ACCOUNT_ID")).longValue(), false));
    db.update(
        "UPDATE transactions SET status='ACTIVE',updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
    event(id, "ACTIVATED");
    return mandate(id, false);
  }

  public Map<String, Object> revoke(String id) {
    var m = mandate(id, true);
    if (!Set.of("REVOKED", "CANCELLED").contains(value(m, "STATUS"))) {
      db.update(
          "UPDATE transactions SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
      event(id, "CANCELLED");
    }
    return mandate(id, false);
  }

  private void checkDates(Map<String, Object> m, boolean executing) {
    LocalDate now = LocalDate.now(ZoneOffset.UTC);
    String start = value(m, "EFFECTIVE_DATE"), end = value(m, "END_DATE");
    if (start == null
        || executing && now.isBefore(LocalDate.parse(start))
        || end != null && now.isAfter(LocalDate.parse(end)))
      throw new InvalidRequestException("Mandate is outside its effective dates");
  }

  public String execute(String id, Execution r) {
    var m = mandate(id, true);
    money(r.amount);
    String tx = requestId(r);
    if (retry(tx, id, "MANDATE_EXECUTION", r.amount)) return tx;
    if (!"ACTIVE".equals(value(m, "STATUS"))
        || !"TRANSFER".equals(value(m, "OPERATION"))
        || m.get("DESTINATION_ACCOUNT_ID") == null)
      throw new InvalidRequestException(
          "An active mandate with a verified beneficiary is required");
    checkDates(m, true);
    if (r.amount.compareTo((BigDecimal) m.get("AMOUNT")) > 0)
      throw new InvalidRequestException("Mandate limit exceeded");
    long from = ((Number) m.get("SOURCE_ACCOUNT_ID")).longValue(),
        to = ((Number) m.get("DESTINATION_ACCOUNT_ID")).longValue();
    lockPair(from, to);
    A a = account(from, false), b = account(to, false);
    owned(a);
    depositAccount(a);
    depositAccount(b);
    if (a.balance.compareTo(r.amount) < 0)
      throw new InvalidRequestException("Insufficient balance");
    post(tx, from, to, r.amount, "TRANSFER", "MANDATE_EXECUTION", id, null, from, to);
    change(from, r.amount.negate());
    change(to, r.amount);
    return tx;
  }

  public List<Map<String, Object>> mandateHistory(String id) {
    mandate(id, false);
    return db.queryForList(
        "SELECT id,record_kind,operation,status,amount,created_at FROM transactions WHERE"
            + " parent_id=? ORDER BY created_at,id",
        id);
  }

  public Map<String, Object> createLoan(LoanRequest r) {
    money(r.principal);
    A funding = account(r.accountId, false);
    owned(funding);
    depositAccount(funding);
    if (r.interestRate == null
        || r.interestRate.signum() < 0
        || r.interestRate.compareTo(new BigDecimal("100")) > 0
        || r.interestRate.scale() > 6
        || r.displayName == null
        || r.displayName.isBlank()
        || r.displayName.length() > 120)
      throw new InvalidRequestException("Supply a name and annual interest rate between 0 and 100");
    if (db.queryForObject(
            "SELECT COUNT(*) FROM customer_credentials WHERE user_id=? AND"
                + " credential_type='PASSWORD' AND password_hash IS NOT NULL",
            Long.class,
            user.userId())
        != 1) throw new InvalidRequestException("The borrower must have login credentials");
    String id = id("L-");
    db.update(
        "INSERT INTO"
            + " accounts(product_id,account_number,customer_id,account_name,account_type,account_category,currency_code,balance,status,created_at,updated_at,version,funding_account_id,principal_amount,interest_rate,product_status,product_type)"
            + " SELECT"
            + " ?,?,customer_id,?,'LOAN','CUSTOMER','INR',0,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,id,?,?,'CREATED','PERSONAL'"
            + " FROM accounts WHERE id=?",
        id,
        "LN" + UUID.randomUUID().toString().replace("-", "").substring(0, 26),
        r.displayName,
        r.principal,
        r.interestRate,
        r.accountId);
    return loan(id, false);
  }

  public Map<String, Object> loan(String id, boolean lock) {
    var rows =
        db.queryForList(
            "SELECT a.* FROM accounts a JOIN customers c ON c.id=a.customer_id WHERE a.product_id=?"
                + " AND a.account_type='LOAN' AND c.user_id=?"
                + (lock ? " FOR UPDATE OF a.balance" : ""),
            id,
            user.userId());
    if (rows.isEmpty()) throw new ResourceNotFoundException("Loan not found");
    return rows.get(0);
  }

  public String disburse(String id) {
    var preview = loan(id, false);
    long loanId = ((Number) preview.get("ID")).longValue(),
        funding = ((Number) preview.get("FUNDING_ACCOUNT_ID")).longValue();
    lockPair(loanId, funding);
    var l = loan(id, false);
    if ("ACTIVE".equals(value(l, "PRODUCT_STATUS"))) {
      var prior =
          db.queryForList(
              "SELECT id FROM transactions WHERE target_id=? AND operation='LOAN_DISBURSEMENT'",
              String.class,
              id);
      if (!prior.isEmpty()) return prior.get(0);
    }
    if (!"CREATED".equals(value(l, "PRODUCT_STATUS")) || !"ACTIVE".equals(value(l, "STATUS")))
      throw new InvalidRequestException("Loan is not awaiting disbursement");
    A a = account(funding, false);
    owned(a);
    depositAccount(a);
    BigDecimal amount = (BigDecimal) l.get("PRINCIPAL_AMOUNT");
    money(amount);
    String tx = id("TX-");
    post(
        tx,
        loanId,
        funding,
        amount,
        "LOAN_DISBURSEMENT",
        "LOAN_DISBURSEMENT",
        null,
        id,
        loanId,
        funding);
    change(loanId, amount);
    change(funding, amount);
    db.update("UPDATE accounts SET product_status='ACTIVE' WHERE id=?", loanId);
    return tx;
  }

  public String repay(String id, Execution r) {
    money(r.amount);
    String tx = requestId(r);
    var preview = loan(id, false);
    long loanId = ((Number) preview.get("ID")).longValue(),
        funding = ((Number) preview.get("FUNDING_ACCOUNT_ID")).longValue();
    lockPair(loanId, funding);
    var l = loan(id, false);
    if (retry(tx, id, "LOAN_REPAYMENT", r.amount)) return tx;
    if (!"ACTIVE".equals(value(l, "PRODUCT_STATUS")) || !"ACTIVE".equals(value(l, "STATUS")))
      throw new InvalidRequestException("Loan is not active");
    A a = account(funding, false);
    owned(a);
    depositAccount(a);
    BigDecimal outstanding = (BigDecimal) l.get("BALANCE");
    if (r.amount.compareTo(outstanding) > 0 || r.amount.compareTo(a.balance) > 0)
      throw new InvalidRequestException(
          "Repayment exceeds outstanding principal or available funds");
    post(
        tx,
        funding,
        loanId,
        r.amount,
        "LOAN_REPAYMENT",
        "LOAN_REPAYMENT",
        null,
        id,
        funding,
        loanId);
    change(funding, r.amount.negate());
    change(loanId, r.amount.negate());
    if (r.amount.compareTo(outstanding) == 0)
      db.update("UPDATE accounts SET product_status='PAID',status='CLOSED' WHERE id=?", loanId);
    return tx;
  }

  private String requestId(Execution r) {
    try {
      return "TX-" + UUID.fromString(r.requestId).toString();
    } catch (Exception e) {
      throw new InvalidRequestException("Supply a UUID requestId for safe retries");
    }
  }

  private boolean retry(String tx, String parent, String op, BigDecimal amount) {
    var rows = db.queryForList("SELECT * FROM transactions WHERE id=?", tx);
    if (rows.isEmpty()) return false;
    var p = rows.get(0);
    if (!user.userId().equals(value(p, "USER_ID"))
        || !op.equals(value(p, "OPERATION"))
        || !parent.equals(value(p, op.equals("MANDATE_EXECUTION") ? "PARENT_ID" : "TARGET_ID"))
        || amount.compareTo((BigDecimal) p.get("AMOUNT")) != 0)
      throw new ConflictException("requestId was already used for another operation");
    return true;
  }

  private void lockPair(long a, long b) {
    account(Math.min(a, b), true);
    account(Math.max(a, b), true);
  }

  private void change(long id, BigDecimal amount) {
    db.update(
        "UPDATE accounts SET balance=balance+?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE"
            + " id=?",
        amount,
        id);
  }

  private void post(
      String id,
      long source,
      long destination,
      BigDecimal amount,
      String type,
      String operation,
      String parent,
      String target,
      long debit,
      long credit) {
    db.update(
        "INSERT INTO"
            + " transactions(id,record_kind,user_id,transaction_type,source_account_id,destination_account_id,amount,currency_code,status,completed_at,operation,parent_id,target_id,transaction_reference)"
            + " VALUES(?,'PAYMENT',?,?,?,?,?,'INR','SUCCESS',CURRENT_TIMESTAMP,?,?,?,?)",
        id,
        user.userId(),
        type,
        source,
        destination,
        amount,
        operation,
        parent,
        target,
        id);
    db.update(
        "INSERT INTO journal_entries(transaction_id,entry_reference,entry_type,status,created_at)"
            + " VALUES(?,?,'TRANSACTION','POSTED',CURRENT_TIMESTAMP)",
        id,
        "J" + id);
    db.update(
        "INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at)"
            + " SELECT id,?,'DEBIT',?,CURRENT_TIMESTAMP FROM journal_entries WHERE"
            + " transaction_id=?",
        debit,
        amount,
        id);
    db.update(
        "INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at)"
            + " SELECT id,?,'CREDIT',?,CURRENT_TIMESTAMP FROM journal_entries WHERE"
            + " transaction_id=?",
        credit,
        amount,
        id);
    BigDecimal net =
        db.queryForObject(
            "SELECT SUM(CASE WHEN entry_type='DEBIT' THEN amount ELSE -amount END) FROM"
                + " ledger_entries WHERE journal_entry_id=(SELECT id FROM journal_entries WHERE"
                + " transaction_id=?)",
            BigDecimal.class,
            id);
    if (net == null || net.signum() != 0) throw new IllegalStateException("Unbalanced journal");
  }
}
