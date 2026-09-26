package com.nexa.api.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Keeps historical demo checksums resolvable without adding demo data to new schemas. */
public final class MigrationLocations {
  private static final String HISTORY = "flyway_schema_history";
  private static final String STANDARD = "classpath:db/migration";
  private static final String HISTORICAL_DEMO = "classpath:db/local-migration";
  private static final String REFERENCE_INITIAL_SCRIPT = "V1__initialize_nexa_schema.sql";
  private static final Map<String, String> DEMO_SCRIPTS =
      Map.of(
          "2", "V2__seed_local_demo_data.sql",
          "4", "V4__enable_local_demo_login.sql",
          "10", "V10__seed_banking_read_models.sql");

  private MigrationLocations() {}

  public static String[] select(Connection connection) throws SQLException {
    return select(connection, connection.getSchema());
  }

  /** The explicit schema supports Flyway default-schema independently of the login user. */
  public static String[] select(Connection connection, String configuredSchema) throws SQLException {
    String schema = configuredSchema;
    if (schema == null || schema.isBlank()) schema = connection.getSchema();
    if (schema == null || schema.isBlank()) {
      throw new IllegalArgumentException("Cannot determine the schema for migration history checks.");
    }
    var metadata = connection.getMetaData();
    boolean historyExists = false;
    // JDBC patterns treat underscores as wildcards; verify exact returned names and owner.
    try (var tables = metadata.getTables(connection.getCatalog(), schema, HISTORY, new String[] {"TABLE"})) {
      while (tables.next()) {
        if (HISTORY.equals(tables.getString("TABLE_NAME"))
            && schema.equals(tables.getString("TABLE_SCHEM"))) {
          historyExists = true;
          break;
        }
      }
    }
    if (!historyExists) return new String[] {STANDARD};

    Set<String> applied = new HashSet<>();
    String history = quote(schema) + "." + quote(HISTORY);
    // Inspect V1 before seed versions: the separate reference project uses V2/V4
    // for onboarding, not demo seeds. Never mistake that history for a partial
    // installation of this project, regardless of the database's row order.
    try (var statement = connection.createStatement();
        var rows = statement.executeQuery(
            "SELECT \"script\" FROM " + history + " WHERE \"version\"='1'")) {
      while (rows.next()) {
        if (REFERENCE_INITIAL_SCRIPT.equals(rows.getString(1))) {
          throw new IllegalArgumentException(
              "This schema belongs to the separate Nexa reference project's migration history"
                  + " (V1__initialize_nexa_schema.sql). This BANK_APP/Nexa project expects"
                  + " V1__create_core_banking_schema.sql. Use a separate dedicated schema such as"
                  + " NEXA_BANK_APP and point both the connection user and schema to it."
                  + " Migration stopped before applying changes. Do not repair, reset or drop"
                  + " the existing schema or its migration history.");
        }
      }
    }
    try (var statement = connection.createStatement();
        var rows = statement.executeQuery(
            "SELECT \"version\", \"script\", \"success\" FROM " + history)) {
      while (rows.next()) {
        String version = rows.getString(1);
        String script = rows.getString(2);
        String expectedScript = version == null ? null : DEMO_SCRIPTS.get(version);
        if (expectedScript == null && (script == null || !DEMO_SCRIPTS.containsValue(script))) continue;
        boolean success = rows.getBoolean(3);
        boolean missingSuccess = rows.wasNull();
        if (expectedScript == null
            || !expectedScript.equals(script)
            || !success
            || missingSuccess
            || !applied.add(version)) {
          throw incompatibleHistory();
        }
      }
    }
    if (applied.isEmpty()) return new String[] {STANDARD};
    if (!applied.equals(DEMO_SCRIPTS.keySet())) throw incompatibleHistory();
    // All seeds already ran: retain their original resources solely for Flyway validation.
    return new String[] {STANDARD, HISTORICAL_DEMO};
  }

  private static IllegalArgumentException incompatibleHistory() {
    return new IllegalArgumentException(
        "Historical demo migration history is partial, failed or inconsistent. Expected successful"
            + " original V2, V4 and V10 seed records together. Migration stopped to prevent adding"
            + " demo data. Inspect and reconcile the existing schema history; no automatic repair,"
            + " baseline or missing-seed execution was performed.");
  }

  private static String quote(String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }
}
