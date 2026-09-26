package com.nexa.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Exact-fixture retirement checks in isolated H2; no Oracle or saved customer data is accessed. */
class LegacyDemoRetirementTest {
  private static final String DEMO_SUBJECT = "usr_01JDEMO000000000000001";

  @Test
  void retiresOnlyExactDemoIdentityWithoutErasingItsFinancialHistory() throws Exception {
    try (Connection db = AccountApplicationTestDatabase.coreDatabase()) {
      customer(db, 101, DEMO_SUBJECT, "Vishal@Example.com");
      customer(db, 102, "real-customer", "real-vishal@example.test");
      credentials(db, "demo", DEMO_SUBJECT);
      credentials(db, "real", "real-customer");
      account(db, 501, 101, "ACTIVE", 1500);
      account(db, 502, 101, "CLOSED", 0);
      account(db, 503, 102, "ACTIVE", 250);
      execute(db, "UPDATE accounts SET balance=1500 WHERE account_number='SYSTEM-CASH'");
      execute(db, "INSERT INTO transactions(id,transaction_type,destination_account_id,amount,status,created_at)"
          + " VALUES ('TX-HISTORICAL-DEMO','DEPOSIT',501,1500,'SUCCESS',CURRENT_TIMESTAMP)");
      execute(db, "INSERT INTO journal_entries(id,transaction_id,entry_reference,entry_type,status,created_at)"
          + " VALUES (601,'TX-HISTORICAL-DEMO','J-HISTORICAL-DEMO','TRANSACTION','POSTED',CURRENT_TIMESTAMP)");
      execute(db, "INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at)"
          + " SELECT 601,id,'DEBIT',1500,CURRENT_TIMESTAMP FROM accounts WHERE account_number='SYSTEM-CASH'");
      execute(db, "INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at)"
          + " VALUES (601,501,'CREDIT',1500,CURRENT_TIMESTAMP)");

      migrate(db);
      assertThat(number(db, "SELECT COUNT(*) FROM customers WHERE id=101 AND status='DISABLED'")).isOne();
      assertThat(number(db, "SELECT COUNT(*) FROM customer_credentials WHERE id='demo-password' AND password_hash IS NULL")).isOne();
      assertThat(number(db, "SELECT COUNT(*) FROM customer_credentials WHERE id='demo-refresh' AND revoked_at IS NOT NULL")).isOne();
      assertThat(number(db, "SELECT COUNT(*) FROM accounts WHERE id=501 AND status='BLOCKED' AND balance=1500 AND version=1")).isOne();
      assertThat(number(db, "SELECT COUNT(*) FROM accounts WHERE id=502 AND status='CLOSED' AND balance=0 AND version=0")).isOne();
      assertThat(number(db, "SELECT COUNT(*) FROM customers WHERE id=102 AND status='ACTIVE'")).isOne();
      assertThat(number(db, "SELECT COUNT(*) FROM accounts WHERE id=503 AND status='ACTIVE' AND balance=250 AND version=0")).isOne();
      assertThat(number(db, "SELECT COUNT(*) FROM customer_credentials WHERE id='real-password' AND password_hash='synthetic-password-hash'")).isOne();
      assertThat(number(db, "SELECT COUNT(*) FROM customer_credentials WHERE id='real-refresh' AND revoked_at IS NULL")).isOne();
      assertThat(number(db, "SELECT COUNT(*) FROM transactions WHERE id='TX-HISTORICAL-DEMO' AND amount=1500 AND status='SUCCESS'")).isOne();
      assertThat(number(db, "SELECT COUNT(*) FROM journal_entries WHERE id=601 AND status='POSTED'")).isOne();
      assertThat(number(db, "SELECT COUNT(*) FROM ledger_entries WHERE journal_entry_id=601 AND amount=1500")).isEqualTo(2);
      assertThat(number(db, "SELECT balance FROM accounts WHERE account_number='SYSTEM-CASH'")).isEqualTo(1500);

      // SQL is safe if rehearsed twice: no balance changes or extra account-version increment.
      migrate(db);
      assertThat(number(db, "SELECT version FROM accounts WHERE id=501")).isOne();
      assertThat(number(db, "SELECT COUNT(*) FROM customer_credentials")).isEqualTo(4);
    }
  }

  @Test
  void subjectOrEmailAloneDoesNotRetireARealCustomer() throws Exception {
    try (Connection db = AccountApplicationTestDatabase.coreDatabase()) {
      customer(db, 101, DEMO_SUBJECT, "different-email@example.test");
      customer(db, 102, "ordinary-customer", "vishal@example.com");
      credentials(db, "subject-only", DEMO_SUBJECT);
      credentials(db, "email-only", "ordinary-customer");
      account(db, 501, 101, "ACTIVE", 100);
      account(db, 502, 102, "ACTIVE", 200);
      migrate(db);
      assertThat(number(db, "SELECT COUNT(*) FROM customers WHERE status='ACTIVE'")).isEqualTo(2);
      assertThat(number(db, "SELECT COUNT(*) FROM accounts WHERE account_category='CUSTOMER' AND status='ACTIVE' AND version=0")).isEqualTo(2);
      assertThat(number(db, "SELECT COUNT(*) FROM customer_credentials WHERE credential_type='PASSWORD' AND password_hash IS NOT NULL")).isEqualTo(2);
      assertThat(number(db, "SELECT COUNT(*) FROM customer_credentials WHERE credential_type='REFRESH' AND revoked_at IS NULL")).isEqualTo(2);
    }
  }

  @Test
  void freshSeedFreeSchemaHasNothingToRetire() throws Exception {
    try (Connection db = AccountApplicationTestDatabase.coreDatabase()) {
      migrate(db);
      assertThat(number(db, "SELECT COUNT(*) FROM customers")).isZero();
      assertThat(number(db, "SELECT COUNT(*) FROM customer_credentials")).isZero();
      assertThat(number(db, "SELECT COUNT(*) FROM accounts WHERE status<>'ACTIVE' OR balance<>0")).isZero();
      assertThat(number(db, "SELECT COUNT(*) FROM transactions")).isZero();
    }
  }

  private static void migrate(Connection db) throws Exception {
    String sql = new ClassPathResource("db/migration/V25__retire_known_demo_identity.sql")
        .getContentAsString(StandardCharsets.UTF_8)
        .replace("SYS_EXTRACT_UTC(SYSTIMESTAMP)", "CURRENT_TIMESTAMP")
        .replace("SYSTIMESTAMP", "CURRENT_TIMESTAMP");
    ScriptUtils.executeSqlScript(db, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
  }

  private static void customer(Connection db, long id, String subject, String email) throws SQLException {
    try (var statement = db.prepareStatement("INSERT INTO customers(id,user_id,full_name,email,created_at,updated_at)"
        + " VALUES (?,?,'Vishal Singh',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
      statement.setLong(1, id); statement.setString(2, subject); statement.setString(3, email); statement.executeUpdate();
    }
  }

  private static void credentials(Connection db, String prefix, String subject) throws SQLException {
    try (var statement = db.prepareStatement("INSERT INTO customer_credentials(id,user_id,credential_type,password_hash)"
        + " VALUES (?,?,'PASSWORD','synthetic-password-hash')")) {
      statement.setString(1, prefix + "-password"); statement.setString(2, subject); statement.executeUpdate();
    }
    try (var statement = db.prepareStatement("INSERT INTO customer_credentials(id,user_id,credential_type,token_hash,expires_at)"
        + " VALUES (?,?,'REFRESH',?,CURRENT_TIMESTAMP + INTERVAL '1' DAY)")) {
      statement.setString(1, prefix + "-refresh"); statement.setString(2, subject);
      statement.setString(3, String.format("%064d", Math.abs(prefix.hashCode()))); statement.executeUpdate();
    }
  }

  private static void account(Connection db, long id, long customer, String status, long amount) throws SQLException {
    try (var statement = db.prepareStatement("INSERT INTO accounts(id,account_number,customer_id,account_name,account_type,"
        + "account_category,balance,status,created_at,updated_at)"
        + " VALUES (?, ?, ?, 'Preserved account','SAVINGS','CUSTOMER',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
      statement.setLong(1, id); statement.setString(2, "ACCOUNT-" + id); statement.setLong(3, customer);
      statement.setLong(4, amount); statement.setString(5, status); statement.executeUpdate();
    }
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
