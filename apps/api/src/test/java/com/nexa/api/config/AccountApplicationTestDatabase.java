package com.nexa.api.config;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Isolated H2 fixture; never connects to Oracle or executes the historical cutover. */
public final class AccountApplicationTestDatabase {
  private AccountApplicationTestDatabase() {}

  /** Keep this connection open while a Spring test uses its JDBC URL. */
  public static Connection initializedDatabase() throws Exception {
    Connection connection = coreDatabase();
    try {
      applyApplicationMigration(connection);
      return connection;
    } catch (Exception | Error error) {
      connection.close();
      throw error;
    }
  }

  static Connection coreDatabase() throws Exception {
    String sql = resource("onboarding-core.sql");
    sql = index(sql, "uk_customer_email_ci", "customers",
        "h2_email_ci VARCHAR2(254) GENERATED ALWAYS AS (LOWER(email))", "h2_email_ci");
    sql = index(sql, "uk_password_customer", "customer_credentials",
        "h2_password_owner VARCHAR2(26) GENERATED ALWAYS AS (CASE WHEN credential_type='PASSWORD' THEN user_id END)",
        "h2_password_owner");
    sql = index(sql, "uk_loan_application", "accounts",
        "h2_loan_owner BIGINT GENERATED ALWAYS AS (CASE WHEN application_key IS NOT NULL THEN customer_id END)",
        "h2_loan_owner,application_key");
    sql = index(sql, "uk_review_payment", "transactions",
        "h2_review_payment VARCHAR2(80) GENERATED ALWAYS AS (CASE WHEN record_kind='TRANSFER_REVIEW' THEN transaction_reference END)",
        "h2_review_payment");
    sql = index(sql, "uk_beneficiary_destination", "transactions",
        "h2_beneficiary_owner VARCHAR2(26) GENERATED ALWAYS AS (CASE WHEN record_kind='BENEFICIARY' THEN user_id END),"
            + "h2_beneficiary_hash VARCHAR2(64) GENERATED ALWAYS AS (CASE WHEN record_kind='BENEFICIARY' THEN destination_hash END)",
        "h2_beneficiary_owner,h2_beneficiary_hash");
    sql = index(sql, "uk_loan_installment", "transactions",
        "h2_installment_target VARCHAR2(80) GENERATED ALWAYS AS (CASE WHEN installment_number IS NOT NULL THEN target_id END)",
        "h2_installment_target,installment_number");
    Connection connection = DriverManager.getConnection(
        "jdbc:h2:mem:account-applications-" + UUID.randomUUID() + ";MODE=Oracle;LOCK_TIMEOUT=10000", "sa", "");
    try {
      execute(connection, sql);
      return connection;
    } catch (Exception | Error error) {
      connection.close();
      throw error;
    }
  }

  static void applyApplicationMigration(Connection connection) throws Exception {
    String sql = resource("db/migration/V24__account_applications.sql");
    sql = index(sql, "uk_app_open_customer", "account_applications",
        "h2_live_owner BIGINT GENERATED ALWAYS AS (CASE WHEN status NOT IN ('REJECTED','CANCELLED','REFUNDED') THEN customer_id END)",
        "h2_live_owner");
    execute(connection, sql);
  }

  private static String resource(String path) throws Exception {
    return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).replace("\uFEFF", "");
  }

  private static void execute(Connection connection, String sql) {
    ScriptUtils.executeSqlScript(connection, new ByteArrayResource(
        sql.replaceAll("\\bSYSTIMESTAMP\\b", "CURRENT_TIMESTAMP").getBytes(StandardCharsets.UTF_8)));
  }

  // H2 cannot parse Oracle function-based indexes. Generated columns preserve
  // their uniqueness behavior here; this does not certify Oracle DDL execution.
  private static String index(String sql, String name, String table, String columns, String keys) {
    Pattern definition = Pattern.compile("(?is)CREATE\\s+UNIQUE\\s+INDEX\\s+" + Pattern.quote(name)
        + "\\s+ON\\s+" + Pattern.quote(table) + "\\s*\\([^;]+;");
    if (definition.matcher(sql).results().count() != 1)
      throw new IllegalStateException("Expected exactly one index definition: " + name);
    return definition.matcher(sql).replaceFirst("ALTER TABLE " + table + " ADD (" + columns
        + "); CREATE UNIQUE INDEX " + name + " ON " + table + "(" + keys + ");");
  }
}
