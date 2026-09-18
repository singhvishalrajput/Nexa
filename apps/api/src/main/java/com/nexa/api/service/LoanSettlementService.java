package com.nexa.api.service;

import com.nexa.api.exep.InvalidRequestException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Bank funding reserve and its loan-control contra account, in the caller's transaction. */
@Service
public class LoanSettlementService {
  private final JdbcTemplate db;

  public LoanSettlementService(JdbcTemplate db) {
    this.db = db;
  }

  public List<Long> accounts() {
    var ids =
        db.queryForList(
            "SELECT id FROM accounts WHERE account_number IN"
                + " ('NEXA-BANK-FUNDING','NEXA-LOAN-CONTROL') AND account_category='SYSTEM' AND"
                + " account_type='CLEARING' AND status='ACTIVE' AND currency_code='INR' ORDER BY"
                + " account_number",
            Long.class);
    if (ids.size() != 2)
      throw new InvalidRequestException("Bank loan settlement accounts are unavailable");
    return ids;
  }

  public void requireFunds(BigDecimal amount) {
    BigDecimal balance =
        db.queryForObject(
            "SELECT balance FROM accounts WHERE account_number='NEXA-BANK-FUNDING'",
            BigDecimal.class);
    if (balance.compareTo(amount) < 0)
      throw new InvalidRequestException("The bank funding account has insufficient funds");
  }

  public void settle(String transaction, BigDecimal principal, boolean disbursement) {
    var ids = accounts();
    long bank = ids.get(0), control = ids.get(1);
    long debit = disbursement ? bank : control, credit = disbursement ? control : bank;
    for (int i = 0; i < 2; i++)
      db.update(
          "INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at) "
              + "SELECT id,?,?,?,CURRENT_TIMESTAMP FROM journal_entries WHERE transaction_id=?",
          i == 0 ? debit : credit,
          i == 0 ? "DEBIT" : "CREDIT",
          principal,
          transaction);
    db.update(
        "UPDATE accounts SET balance=balance-?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE"
            + " id=?",
        principal,
        debit);
    db.update(
        "UPDATE accounts SET balance=balance+?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE"
            + " id=?",
        principal,
        credit);
    // Public movement identifies the bank counterparty; the loan remains linked through target_id
    // and ledger postings.
    db.update(
        "UPDATE transactions SET "
            + (disbursement ? "source_account_id" : "destination_account_id")
            + "=? WHERE id=?",
        bank,
        transaction);
  }
}
