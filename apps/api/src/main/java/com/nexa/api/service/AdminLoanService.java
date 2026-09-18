package com.nexa.api.service;

import com.nexa.api.exep.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminLoanService {
  private final JdbcTemplate db;
  private final CurrentUserProvider user;

  public AdminLoanService(JdbcTemplate db, CurrentUserProvider user) {
    this.db = db;
    this.user = user;
  }

  private void authorize() {
    if (db.queryForObject(
            "SELECT COUNT(*) FROM customers WHERE user_id=? AND role='ADMIN' AND status='ACTIVE'",
            Integer.class,
            user.userId())
        != 1)
      throw new org.springframework.security.access.AccessDeniedException(
          "Administrator access required");
  }

  public List<Map<String, Object>> requests() {
    authorize();
    return db.queryForList(
        "SELECT"
            + " a.id,a.product_id,a.account_name,a.principal_amount,a.interest_rate,a.tenure_months,a.periodic_payment,a.loan_purpose,a.product_status,a.created_at,a.funding_account_id,c.full_name,c.email"
            + " FROM accounts a JOIN customers c ON c.id=a.customer_id WHERE a.account_type='LOAN'"
            + " AND a.product_status='PENDING_APPROVAL' ORDER BY a.created_at,a.id");
  }

  public record Decision(String reason) {}

  public Map<String, Object> decide(String productId, Decision request, boolean approve) {
    authorize();
    if (request == null
        || request.reason() == null
        || request.reason().isBlank()
        || request.reason().length() > 500)
      throw new InvalidRequestException("Enter a decision reason of 1–500 characters");
    var rows =
        db.queryForList(
            "SELECT * FROM accounts WHERE product_id=? AND account_type='LOAN' FOR UPDATE",
            productId);
    if (rows.isEmpty()) throw new ResourceNotFoundException("Loan request not found");
    var loan = rows.get(0);
    String state = approve ? "APPROVED" : "REJECTED";
    if (state.equals(loan.get("PRODUCT_STATUS"))) return loan;
    if (!"PENDING_APPROVAL".equals(loan.get("PRODUCT_STATUS")))
      throw new ConflictException("This loan request has already been reviewed");
    if (!"ACTIVE".equals(loan.get("STATUS")))
      throw new InvalidRequestException("The loan account must be active");
    if (db.queryForObject(
            "SELECT COUNT(*) FROM accounts a JOIN customers c ON c.id=a.customer_id WHERE a.id=?"
                + " AND a.customer_id=? AND a.status='ACTIVE' AND c.status='ACTIVE' AND"
                + " EXISTS(SELECT 1 FROM customer_credentials x WHERE x.user_id=c.user_id AND"
                + " x.credential_type='PASSWORD' AND x.password_hash IS NOT NULL)",
            Integer.class,
            loan.get("FUNDING_ACCOUNT_ID"),
            loan.get("CUSTOMER_ID"))
        != 1)
      throw new InvalidRequestException(
          "An active borrower with login credentials and a funding account is required");
    db.update(
        "UPDATE accounts SET"
            + " product_status=?,reviewed_by=?,review_reason=?,reviewed_at=CURRENT_TIMESTAMP,"
            + "approved_at="
            + (approve ? "CURRENT_TIMESTAMP" : "NULL")
            + ",version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
        state,
        user.userId(),
        request.reason().trim(),
        loan.get("ID"));
    db.update(
        "INSERT INTO"
            + " transactions(id,record_kind,user_id,source_account_id,target_id,operation,status,audit_reason,before_status,after_status,created_at)"
            + " VALUES(?,'ADMIN_EVENT',?,?,?,?,'COMPLETED',?,'PENDING_APPROVAL',?,CURRENT_TIMESTAMP)",
        "A-" + UUID.randomUUID(),
        user.userId(),
        loan.get("ID"),
        productId,
        approve ? "APPROVE_LOAN" : "REJECT_LOAN",
        request.reason().trim(),
        state);
    return db.queryForMap("SELECT * FROM accounts WHERE id=?", loan.get("ID"));
  }
}
