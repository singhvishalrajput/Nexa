package com.nexa.api.banking;

import org.springframework.jdbc.core.JdbcTemplate;

/** Isolated financial-test fixtures; authorization itself is exercised through HTTP elsewhere. */
public final class LoanTestSupport {
  private LoanTestSupport() {}

  public static void bank(JdbcTemplate db) {
    for (String number : java.util.List.of("NEXA-BANK-FUNDING", "NEXA-LOAN-CONTROL")) {
      if (db.queryForObject(
              "SELECT COUNT(*) FROM accounts WHERE account_number=?", Integer.class, number)
          == 0)
        db.update(
            "INSERT INTO"
                + " accounts(account_number,account_name,account_type,account_category,currency_code,balance,status,version,created_at,updated_at)"
                + " VALUES(?,?,'CLEARING','SYSTEM','INR',?,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
            number,
            number,
            number.equals("NEXA-BANK-FUNDING") ? 10000000 : 0);
    }
  }

  public static void approvedFixture(JdbcTemplate db, String id) {
    bank(db);
    db.update(
        "UPDATE accounts SET product_status='APPROVED',approved_at=CURRENT_TIMESTAMP WHERE"
            + " product_id=?",
        id);
  }
}
