package com.nexa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/** Exercises the actual additive V24 SQL in isolated H2 with expression-index adaptation. */
class AccountApplicationSchemaTest {
  @Test
  void upgradePreservesExistingDuplicateSavingsAndBalancesWithoutCreatingMoney() throws Exception {
    try (Connection db = AccountApplicationTestDatabase.coreDatabase()) {
      customer(db);
      execute(db, "INSERT INTO accounts(account_number,customer_id,account_name,account_type,account_category,"
          + "balance,status,created_at,updated_at) VALUES ('OLD-SAVINGS-1',101,'Existing one','SAVINGS','CUSTOMER',"
          + "1250,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
      execute(db, "INSERT INTO accounts(account_number,customer_id,account_name,account_type,account_category,"
          + "balance,status,created_at,updated_at) VALUES ('OLD-SAVINGS-2',101,'Existing two','SAVINGS','CUSTOMER',"
          + "2500,'BLOCKED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
      AccountApplicationTestDatabase.applyApplicationMigration(db);
      assertThat(number(db, "SELECT COUNT(*) FROM accounts WHERE customer_id=101")).isEqualTo(2);
      assertThat(number(db, "SELECT SUM(balance) FROM accounts WHERE customer_id=101")).isEqualTo(3750);
      assertThat(number(db, "SELECT COUNT(*) FROM accounts WHERE account_number='NEXA-OPENING-HOLD'"
          + " AND balance=0 AND status='ACTIVE' AND account_category='SYSTEM' AND customer_id IS NULL")).isOne();
      assertThat(number(db, "SELECT COUNT(*) FROM transactions")).isZero();
      assertThat(number(db, "SELECT COUNT(*) FROM journal_entries")).isZero();
      assertThat(number(db, "SELECT COUNT(*) FROM ledger_entries")).isZero();
    }
  }

  @Test
  void holdingAccountMergeDoesNotOverwriteAnExistingReservedRow() throws Exception {
    try (Connection db = AccountApplicationTestDatabase.coreDatabase()) {
      execute(db, "INSERT INTO accounts(account_number,account_name,account_type,account_category,balance,"
          + "status,created_at,updated_at) VALUES ('NEXA-OPENING-HOLD','Existing holding','CLEARING','SYSTEM',"
          + "750,'BLOCKED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
      AccountApplicationTestDatabase.applyApplicationMigration(db);
      assertThat(number(db, "SELECT COUNT(*) FROM accounts WHERE account_number='NEXA-OPENING-HOLD'"
          + " AND balance=750 AND status='BLOCKED' AND account_name='Existing holding'")).isOne();
    }
  }

  @Test
  void applicationAmountHasExactPaiseAndOneCroreBounds() throws Exception {
    try (Connection db = AccountApplicationTestDatabase.initializedDatabase()) {
      customer(db);
      application(db);
      for (String amount : new String[] {"999.99", "10000000.01", "1000.001", "NULL"}) {
        assertThatThrownBy(() -> execute(db,
            "UPDATE account_applications SET requested_amount=" + amount + " WHERE id='application-1'"))
            .as("reject invalid amount %s", amount).isInstanceOf(SQLException.class);
      }
      execute(db, "UPDATE account_applications SET requested_amount=10000000 WHERE id='application-1'");
      assertThat(number(db, "SELECT requested_amount FROM account_applications")).isEqualTo(10000000);
    }
  }

  @Test
  void identityMetadataCannotRetainFullAadhaarOrMissingEncryptionMetadata() throws Exception {
    try (Connection db = AccountApplicationTestDatabase.initializedDatabase()) {
      customer(db);
      application(db);
      for (String change : new String[] {"identity_last4='123456789012'", "identity_last4=NULL",
          "identity_ciphertext='synthetic-extra-private-identity-data'", "identity_type='PAN'",
          "identity_type='PASSPORT'"}) {
        assertThatThrownBy(() -> execute(db,
            "UPDATE account_applications SET " + change + " WHERE id='application-1'"))
            .as("reject invalid identity shape %s", change).isInstanceOf(SQLException.class);
      }
      execute(db, "UPDATE account_applications SET identity_type='PAN',identity_last4='234A',"
          + "identity_ciphertext='synthetic-envelope-containing-more-than-forty-characters',"
          + "identity_key_id='test-key' WHERE id='application-1'");
    }
  }

  @Test
  void oneLiveApplicationAndFingerprintAuditAreEnforced() throws Exception {
    try (Connection db = AccountApplicationTestDatabase.initializedDatabase()) {
      customer(db);
      application(db);
      assertThatThrownBy(() -> execute(db, applicationSql().replace("application-1", "application-2")
          .replace("request-1", "request-2"))).isInstanceOf(SQLException.class);
      String event = "INSERT INTO application_events(id,application_id,event_key,application_version,event_type,"
          + "actor_user_id,actor_role,to_status,correlation_id,request_fingerprint) VALUES"
          + "('event-1','application-1','event-request',0,'APPLICATION_CREATED','application-owner',"
          + "'CUSTOMER','DRAFT','correlation','" + "a".repeat(64) + "')";
      assertThatThrownBy(() -> execute(db, event.replace("a".repeat(64), "invalid")))
          .isInstanceOf(SQLException.class);
      execute(db, event);
      assertThat(number(db, "SELECT COUNT(*) FROM application_events")).isOne();
    }
  }

  private static void customer(Connection db) throws SQLException {
    execute(db, "INSERT INTO customers(id,user_id,full_name,email,created_at,updated_at) VALUES"
        + "(101,'application-owner','Synthetic applicant','applicant@example.test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
  }

  private static void application(Connection db) throws SQLException { execute(db, applicationSql()); }

  private static String applicationSql() {
    return "INSERT INTO account_applications(id,customer_id,request_key,full_name,email,date_of_birth,business_date,"
        + "requested_amount,consent_notice_version,consented_at,created_at,updated_at,identity_type,identity_last4)"
        + " VALUES ('application-1',101,'request-1','Synthetic applicant','applicant@example.test',"
        + "DATE '1990-01-01',DATE '2026-09-25',1000,'in-person-identity-v1',CURRENT_TIMESTAMP,"
        + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'AADHAAR','1234')";
  }

  private static void execute(Connection db, String sql) throws SQLException {
    try (var statement = db.createStatement()) { statement.executeUpdate(sql); }
  }

  private static long number(Connection db, String sql) throws SQLException {
    try (var statement = db.createStatement(); var result = statement.executeQuery(sql)) {
      result.next(); return result.getLong(1);
    }
  }
}
