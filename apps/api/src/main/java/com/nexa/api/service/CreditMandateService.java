package com.nexa.api.service;

import com.nexa.api.beans.LoanModels;
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
  private final LoanCalculationService calculation;
  private final Clock clock;
  private final BusinessDateResolver dates;
  private final LoanSettlementService settlement;

  public CreditMandateService(
      JdbcTemplate db,
      CurrentUserProvider user,
      LoanCalculationService calculation,
      Clock clock,
      BusinessDateResolver dates,
      LoanSettlementService settlement) {
    this.db = db;
    this.user = user;
    this.calculation = calculation;
    this.clock = clock;
    this.dates = dates;
    this.settlement = settlement;
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
      long accountId,
      String displayName,
      BigDecimal principal,
      BigDecimal interestRate,
      String applicationKey,
      String purpose,
      BigDecimal amount,
      Integer tenureMonths) {
    public LoanRequest(
        long accountId, String displayName, BigDecimal principal, BigDecimal interestRate) {
      this(accountId, displayName, principal, interestRate, null, null, null, null);
    }

    public boolean scheduled() {
      return applicationKey != null || purpose != null || amount != null || tenureMonths != null;
    }
  }

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
    if (r == null) throw new InvalidRequestException("Loan details are required");
    if (r.scheduled()) return applyLoan(r);
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
            + " ?,?,customer_id,?,'LOAN','CUSTOMER','INR',0,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,id,?,?,'PENDING_APPROVAL','PERSONAL'"
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
    lockLoanAccounts(loanId, funding);
    var l = loan(id, false);
    if (Set.of("ACTIVE", "PAID", "CLOSED", "OVERDUE").contains(value(l, "PRODUCT_STATUS"))) {
      var prior =
          db.queryForList(
              "SELECT id FROM transactions WHERE target_id=? AND operation='LOAN_DISBURSEMENT'",
              String.class,
              id);
      if (!prior.isEmpty()) return prior.get(0);
    }
    if (!"APPROVED".equals(value(l, "PRODUCT_STATUS")) || !"ACTIVE".equals(value(l, "STATUS")))
      throw new InvalidRequestException(
          "An administrator must approve the loan before disbursement");
    A a = account(funding, false);
    owned(a);
    depositAccount(a);
    BigDecimal amount = (BigDecimal) l.get("PRINCIPAL_AMOUNT");
    money(amount);
    settlement.requireFunds(amount);
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
    settlement.settle(tx, amount, true);
    db.update("UPDATE accounts SET product_status='ACTIVE' WHERE id=?", loanId);
    if (l.get("TENURE_MONTHS") != null) {
      var schedule =
          calculation.schedule(
              amount,
              (BigDecimal) l.get("INTEREST_RATE"),
              ((Number) l.get("TENURE_MONTHS")).intValue(),
              loanDate());
      for (var row : schedule) {
        db.update(
            "INSERT INTO transactions(id,record_kind,user_id,target_id,source_account_id,"
                + "installment_number,due_at,principal_component,interest_component,amount,currency_code,status,created_at)"
                + " VALUES(?,'LOAN_INSTALLMENT',?,?,?,?,?,?,?,?,'INR','PENDING',CURRENT_TIMESTAMP)",
            id("I-"),
            user.userId(),
            id,
            funding,
            row.installmentNumber(),
            row.dueDate().toString(),
            row.principalAmount(),
            row.interestAmount(),
            row.totalAmount());
      }
      db.update(
          "UPDATE accounts SET due_at=? WHERE id=?", schedule.get(0).dueDate().toString(), loanId);
    }
    db.update(
        "UPDATE transactions SET principal_component=?,interest_component=0 WHERE id=?",
        amount,
        tx);
    return tx;
  }

  public String repay(String id, Execution r) {
    money(r.amount);
    String tx = requestId(r);
    var preview = loan(id, false);
    if (preview.get("TENURE_MONTHS") != null) return payInstallment(id, null, r).id();
    long loanId = ((Number) preview.get("ID")).longValue(),
        funding = ((Number) preview.get("FUNDING_ACCOUNT_ID")).longValue();
    lockLoanAccounts(loanId, funding);
    var l = loan(id, false);
    if (retry(tx, id, "LOAN_REPAYMENT", r.amount)) return tx;
    if (!Set.of("ACTIVE", "OVERDUE").contains(value(l, "PRODUCT_STATUS"))
        || !"ACTIVE".equals(value(l, "STATUS")))
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
    settlement.settle(tx, r.amount, false);
    if (r.amount.compareTo(outstanding) == 0)
      db.update("UPDATE accounts SET product_status='PAID',status='CLOSED' WHERE id=?", loanId);
    return tx;
  }

  private LocalDate loanDate() {
    return LocalDate.now(clock.withZone(dates.zone()));
  }

  private Map<String, Object> applyLoan(LoanRequest r) {
    if (r.accountId <= 0
        || r.applicationKey == null
        || !r.applicationKey.matches("[A-Za-z0-9_-]{1,80}")
        || r.purpose == null
        || r.purpose.isBlank()
        || r.purpose.length() > 200)
      throw new InvalidRequestException(
          "Supply accountId, applicationKey (1 to 80 letters, digits, underscores or hyphens), and"
              + " purpose (up to 200 characters)");
    if (r.principal != null || r.interestRate != null)
      throw new InvalidRequestException(
          "Use amount and tenureMonths; application rates are set by the bank");
    var quote = calculation.quote(new LoanModels.QuoteRequest(r.amount, r.tenureMonths));
    // Serialize application keys across all accounts belonging to the same customer.
    db.queryForObject(
        "SELECT id FROM customers WHERE user_id=? FOR UPDATE", Long.class, user.userId());
    A funding = account(r.accountId, true);
    owned(funding);
    depositAccount(funding);
    var existing =
        db.queryForList(
            "SELECT a.* FROM accounts a JOIN customers c ON c.id=a.customer_id"
                + " WHERE c.user_id=? AND a.application_key=?",
            user.userId(),
            r.applicationKey);
    if (!existing.isEmpty()) {
      var prior = existing.get(0);
      if (((Number) prior.get("FUNDING_ACCOUNT_ID")).longValue() != r.accountId
          || ((BigDecimal) prior.get("PRINCIPAL_AMOUNT")).compareTo(quote.amount()) != 0
          || ((Number) prior.get("TENURE_MONTHS")).intValue() != r.tenureMonths
          || !r.purpose.trim().equals(prior.get("LOAN_PURPOSE")))
        throw new ConflictException("Application key was already used with different loan details");
      return prior;
    }
    var created =
        createLoan(
            new LoanRequest(
                r.accountId,
                r.purpose.trim().substring(0, Math.min(120, r.purpose.trim().length())),
                quote.amount(),
                quote.annualInterestRate()));
    String id = value(created, "PRODUCT_ID");
    db.update(
        "UPDATE accounts SET application_key=?,loan_purpose=?,tenure_months=?,periodic_payment=?,"
            + "product_status='PENDING_APPROVAL',approved_at=NULL WHERE product_id=?",
        r.applicationKey,
        r.purpose.trim(),
        r.tenureMonths,
        quote.emiAmount(),
        id);
    return loan(id, false);
  }

  public List<LoanModels.Installment> schedule(String id) {
    loan(id, false);
    return db.query(
        "SELECT * FROM transactions WHERE record_kind='LOAN_INSTALLMENT' AND target_id=? ORDER BY"
            + " installment_number",
        (r, n) -> {
          LocalDate due = LocalDate.parse(r.getString("due_at"));
          String state = r.getString("status");
          return new LoanModels.Installment(
              r.getString("id"),
              id,
              r.getInt("installment_number"),
              due,
              r.getBigDecimal("principal_component"),
              r.getBigDecimal("interest_component"),
              r.getBigDecimal("amount"),
              "PENDING".equals(state) && due.isBefore(loanDate()) ? "OVERDUE" : state,
              r.getTimestamp("completed_at") == null
                  ? null
                  : r.getTimestamp("completed_at").toLocalDateTime());
        },
        id);
  }

  public List<LoanModels.Payment> loanPayments(String id) {
    var l = loan(id, false);
    long funding = ((Number) l.get("FUNDING_ACCOUNT_ID")).longValue();
    return db.query(
        "SELECT * FROM transactions WHERE record_kind='PAYMENT' AND target_id=? AND operation IN"
            + " ('LOAN_DISBURSEMENT','LOAN_REPAYMENT') ORDER BY created_at DESC,id",
        (r, n) ->
            new LoanModels.Payment(
                r.getString("id"),
                id,
                r.getString("parent_id"),
                funding,
                r.getString("transaction_reference"),
                "LOAN_DISBURSEMENT".equals(r.getString("operation"))
                    ? "DISBURSEMENT"
                    : r.getString("parent_id") == null ? "PRINCIPAL_PREPAYMENT" : "EMI_PAYMENT",
                "INR",
                r.getBigDecimal("amount"),
                r.getBigDecimal("principal_component"),
                r.getBigDecimal("interest_component"),
                "POSTED",
                r.getTimestamp("completed_at").toLocalDateTime()),
        id);
  }

  public LoanModels.Payment payInstallment(String id, String installmentId) {
    return payInstallment(id, installmentId, null);
  }

  public LoanModels.RepaymentOptions repaymentOptions(String id) {
    var l = loan(id, false);
    return repaymentOptions(l, schedule(id), false);
  }

  public void validateRepayment(String id, BigDecimal amount) {
    money(amount);
    validateRepayment(amount, repaymentOptions(id));
  }

  private void validateRepayment(BigDecimal amount, LoanModels.RepaymentOptions options) {
    if (amount.compareTo(options.minimumAmount()) < 0)
      throw new InvalidRequestException("Pay at least the next EMI amount: " + options.minimumAmount());
    if (amount.compareTo(options.maximumAmount()) > 0)
      throw new InvalidRequestException(
          "Repayment exceeds the payoff amount: " + options.maximumAmount());
  }

  private LoanModels.RepaymentOptions repaymentOptions(
      Map<String, Object> l, List<LoanModels.Installment> rows, boolean explicitInstallment) {
    if (!"ACTIVE".equals(value(l, "PRODUCT_STATUS")) || !"ACTIVE".equals(value(l, "STATUS")))
      throw new InvalidRequestException("Only an active loan can be repaid");
    BigDecimal balance = (BigDecimal) l.get("BALANCE");
    if (l.get("TENURE_MONTHS") == null)
      return new LoanModels.RepaymentOptions(
          new BigDecimal("0.01"), balance, BigDecimal.ZERO, true, null, 0, null);
    var unpaid = rows.stream().filter(r -> !"PAID".equals(r.status())).toList();
    if (unpaid.isEmpty()) throw new InvalidRequestException("No unpaid loan installments remain");
    var next = unpaid.get(0);
    LocalDate monthStart = loanDate().withDayOfMonth(1);
    // Paying a future EMI early also covers the current month. Overdue/current-month EMIs
    // must still be settled before a small payment may go entirely to principal.
    boolean principalOnly =
        !explicitInstallment
            && !next.dueDate().isBefore(monthStart.plusMonths(1))
            && rows.stream()
                .anyMatch(
                    r -> "PAID".equals(r.status()) && !r.dueDate().isBefore(monthStart));
    BigDecimal interest = principalOnly ? BigDecimal.ZERO : next.interestAmount();
    return new LoanModels.RepaymentOptions(
        principalOnly ? new BigDecimal("0.01") : next.totalAmount(),
        balance.add(interest),
        interest,
        principalOnly,
        (BigDecimal) l.get("PERIODIC_PAYMENT"),
        unpaid.size(),
        unpaid.get(unpaid.size() - 1).dueDate());
  }

  private LoanModels.Payment payInstallment(String id, String installmentId, Execution execution) {
    var preview = loan(id, false);
    long loanId = ((Number) preview.get("ID")).longValue();
    long funding = ((Number) preview.get("FUNDING_ACCOUNT_ID")).longValue();
    var incomeIds =
        db.queryForList(
            "SELECT id FROM accounts WHERE account_number='NEXA-LOAN-INTEREST' AND"
                + " account_type='CLEARING' AND account_category='SYSTEM' AND status='ACTIVE' AND"
                + " currency_code='INR'",
            Long.class);
    if (incomeIds.size() != 1)
      throw new IllegalStateException("Loan interest account is unavailable");
    long income = incomeIds.get(0);
    // Use the same global account lock order as transfers, including the income account.
    lockLoanAccounts(loanId, funding, income);
    var l = loan(id, false);
    String tx = execution == null ? id("TX-") : requestId(execution);
    if (execution != null && retry(tx, id, "LOAN_REPAYMENT", execution.amount))
      return loanPayments(id).stream().filter(p -> tx.equals(p.id())).findFirst().orElseThrow();
    var rows = schedule(id);
    var installment =
        rows.stream()
            .filter(
                r ->
                    installmentId == null
                        ? !"PAID".equals(r.status())
                        : r.id().equals(installmentId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Loan installment not found"));
    if ("PAID".equals(installment.status()))
      return loanPayments(id).stream()
          .filter(p -> installment.id().equals(p.installmentId()))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("Installment payment is missing"));
    var next = rows.stream().filter(r -> !"PAID".equals(r.status())).findFirst().orElseThrow();
    if (!next.id().equals(installment.id()))
      throw new InvalidRequestException("Pay the earliest unpaid installment first");
    var options = repaymentOptions(l, rows, installmentId != null);
    BigDecimal amount = execution == null ? installment.totalAmount() : execution.amount;
    validateRepayment(amount, options);
    BigDecimal interest = options.interestAmount();
    BigDecimal principal = amount.subtract(interest);
    BigDecimal remaining = ((BigDecimal) l.get("BALANCE")).subtract(principal);
    A source = account(funding, false);
    owned(source);
    depositAccount(source);
    if (source.balance.compareTo(amount) < 0)
      throw new InvalidRequestException("Insufficient balance for this repayment");
    post(
        tx,
        funding,
        loanId,
        amount,
        "LOAN_REPAYMENT",
        "LOAN_REPAYMENT",
        options.principalOnly() ? null : installment.id(),
        id,
        funding,
        loanId,
        income,
        interest);
    db.update(
        "UPDATE transactions SET principal_component=?,interest_component=? WHERE id=?",
        principal,
        interest,
        tx);
    change(funding, amount.negate());
    change(loanId, principal.negate());
    change(income, interest);
    settlement.settle(tx, principal, false);
    if (!options.principalOnly())
      db.update(
          "UPDATE transactions SET status='PAID',completed_at=?,transaction_reference=? WHERE id=?",
          LocalDateTime.now(clock),
          tx,
          installment.id());
    var unpaid =
        rows.stream()
            .filter(r -> !"PAID".equals(r.status()))
            .filter(r -> options.principalOnly() || !r.id().equals(installment.id()))
            .toList();
    if (options.principalOnly() || amount.compareTo(installment.totalAmount()) > 0) {
      var revised =
          calculation.recalculate(
              remaining, (BigDecimal) l.get("INTEREST_RATE"), options.regularEmi(), unpaid);
      for (var row : revised)
        db.update(
            "UPDATE transactions SET principal_component=?,interest_component=?,amount=? WHERE id=?",
            row.principalAmount(),
            row.interestAmount(),
            row.totalAmount(),
            row.id());
      // These are unpaid projections, not payment records; retain all paid rows and receipts.
      for (int i = revised.size(); i < unpaid.size(); i++)
        db.update(
            "DELETE FROM transactions WHERE id=? AND record_kind='LOAN_INSTALLMENT'"
                + " AND status='PENDING'",
            unpaid.get(i).id());
      unpaid = revised;
    }
    String nextDate = unpaid.isEmpty() ? null : unpaid.get(0).dueDate().toString();
    db.update("UPDATE accounts SET due_at=? WHERE id=?", nextDate, loanId);
    if (remaining.signum() == 0)
      db.update(
          "UPDATE accounts SET product_status='CLOSED',status='CLOSED',closed_at=?,due_at=NULL"
              + " WHERE id=?",
          LocalDateTime.now(clock),
          loanId);
    return loanPayments(id).stream().filter(p -> tx.equals(p.id())).findFirst().orElseThrow();
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

  private void lockLoanAccounts(long... ids) {
    java.util.stream.LongStream.concat(
            java.util.Arrays.stream(ids), settlement.accounts().stream().mapToLong(Long::longValue))
        .distinct()
        .sorted()
        .forEach(a -> account(a, true));
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
    post(
        id,
        source,
        destination,
        amount,
        type,
        operation,
        parent,
        target,
        debit,
        credit,
        null,
        BigDecimal.ZERO);
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
      long credit,
      Long interestAccount,
      BigDecimal interest) {
    db.update(
        "INSERT INTO"
            + " transactions(id,record_kind,user_id,transaction_type,source_account_id,destination_account_id,amount,currency_code,status,completed_at,operation,parent_id,target_id,transaction_reference,created_at)"
            + " VALUES(?,'PAYMENT',?,?,?,?,?,'INR','SUCCESS',CURRENT_TIMESTAMP,?,?,?,?,CURRENT_TIMESTAMP)",
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
        amount.subtract(interest),
        id);
    if (interest.signum() > 0)
      db.update(
          "INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at)"
              + " SELECT id,?,'CREDIT',?,CURRENT_TIMESTAMP FROM journal_entries WHERE"
              + " transaction_id=?",
          interestAccount,
          interest,
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
