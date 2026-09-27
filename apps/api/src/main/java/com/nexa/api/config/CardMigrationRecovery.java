package com.nexa.api.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.ValidateResult;
import org.flywaydb.core.internal.info.MigrationInfoImpl;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;
import org.flywaydb.core.internal.resource.StringResource;

/** Narrow, opt-in reconciliation of the fully committed V31 schema after its final redundant index failed. */
public final class CardMigrationRecovery {
  static final String SCRIPT = "V31__local_card_applications.sql";
  static final String OLD_INDEX = "CREATE INDEX idx_card_review_queue ON accounts(account_type,product_status,created_at);";
  private static final String HISTORY = "flyway_schema_history";
  private static final Set<String> COLUMN_NAMES = Set.of("CARD_REQUEST_ID", "CARD_REQUEST_HASH", "CARD_SLOT_KEY", "CARD_CUSTOMER_BLOCK");
  private static final Set<String> INDEX_NAMES = Set.of("UK_CARD_REQUEST", "UK_CARD_SLOT", "IDX_LOAN_REVIEW_QUEUE");

  private CardMigrationRecovery() {}

  record History(int rank, String version, String description, String type, String script,
      Integer checksum, boolean success, String installedBy, String installedOn, int executionTime) {}
  record Snapshot(List<History> history, List<Map<String, Object>> columns,
      List<Map<String, Object>> indexes, List<Map<String, Object>> constraints) {}
  record Definition(int oldChecksum, int correctedChecksum, Map<String, String> checks) {}

  /** Inspection is read-only. Apply changes one verified history row; it never executes schema DDL. */
  public static void run(Connection connection, Flyway flyway, String schema, boolean apply) throws SQLException {
    require(schema != null && schema.matches("NEXA_[A-Z0-9_]{1,25}"), "Use a dedicated uppercase NEXA_ schema.");
    Definition definition = definition();
    owner(connection, schema);
    String[] locations = MigrationLocations.select(connection, schema);
    require(schema.equals(flyway.getConfiguration().getDefaultSchema()), "Flyway and recovery must use the same schema.");
    require(Set.copyOf(Arrays.stream(flyway.getConfiguration().getLocations()).map(Object::toString).toList())
        .equals(Set.copyOf(Arrays.asList(locations))), "Flyway migration locations do not match the inspected history.");
    require(flyway.getConfiguration().getCallbacks().length == 0, "Recovery cannot run with custom Flyway callbacks.");
    Snapshot inspected = snapshot(connection, schema);
    History target = verify(inspected, definition);
    validate(flyway, inspected.history(), target, definition);
    System.out.println("V31 recovery inspection verified the original history, four card columns, two unique indexes,"
        + " two validated checks, and the existing shared review index. No account data was read or changed.");
    if (target.success()) {
      System.out.println("V31 is already recorded with the corrected checksum. No recovery changes are needed.");
      return;
    }
    if (!apply) {
      System.out.println("The known final-index failure is recoverable. Inspection made no changes."
          + " Stop the API and other setup processes, then rerun setup with -RecoverCardMigration to reconcile only V31.");
      return;
    }
    require(connection.getAutoCommit(), "Recovery requires a new connection without an existing transaction.");
    boolean committed = false;
    connection.setAutoCommit(false);
    try {
      // NOWAIT makes a running migration or account transaction an explicit retry, not a hidden long wait.
      execute(connection, "LOCK TABLE " + qualified(schema, HISTORY) + " IN EXCLUSIVE MODE NOWAIT");
      execute(connection, "LOCK TABLE " + qualified(schema, "ACCOUNTS") + " IN EXCLUSIVE MODE NOWAIT");
      owner(connection, schema);
      require(Arrays.equals(locations, MigrationLocations.select(connection, schema)), "Migration locations changed during recovery.");
      Snapshot locked = snapshot(connection, schema);
      require(inspected.equals(locked), "Schema or migration history changed after inspection. No recovery was applied.");
      History failed = verify(locked, definition);
      // Validation uses a separate connection before our update, so it sees the still-committed failed row.
      validate(flyway, locked.history(), failed, definition);
      String sql = "UPDATE " + qualified(schema, HISTORY)
          + " SET \"success\"=1,\"checksum\"=? WHERE \"installed_rank\"=? AND \"version\"='31'"
          + " AND \"script\"=? AND \"type\"='SQL' AND \"success\"=0 AND \"checksum\"=?";
      try (var statement = connection.prepareStatement(sql)) {
        statement.setQueryTimeout(15);
        statement.setInt(1, definition.correctedChecksum());
        statement.setInt(2, failed.rank());
        statement.setString(3, SCRIPT);
        statement.setInt(4, definition.oldChecksum());
        require(statement.executeUpdate() == 1, "The failed V31 row changed. No recovery was applied.");
      }
      // Compare every other history field and row before committing the narrowly scoped update.
      var expected = new ArrayList<>(locked.history());
      expected.set(expected.size() - 1, new History(failed.rank(), failed.version(), failed.description(), failed.type(),
          failed.script(), definition.correctedChecksum(), true, failed.installedBy(), failed.installedOn(), failed.executionTime()));
      require(expected.equals(history(connection, schema)), "Unexpected migration history change. Recovery was rolled back.");
      connection.commit();
      committed = true;
    } finally {
      if (!committed) connection.rollback();
      connection.setAutoCommit(true);
    }
    // A connection failure here does not undo the committed reconciliation; a repeat inspection recognizes it.
    try {
      var finalHistory = history(connection, schema);
      validate(flyway, finalHistory, finalHistory.get(finalHistory.size() - 1), definition);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("V31 reconciliation committed, but final validation failed. Keep the API stopped and inspect history; do not repeat a broad repair. "
          + invalid.getMessage(), invalid);
    }
    System.out.println("Recovered only failed V31 history after verifying its complete schema. Recorded migrations now validate; later pending migrations remain unapplied."
        + " No accounts, balances, other history rows, tables, columns, indexes, or constraints were changed.");
  }

  static Definition definition() {
    try (var stream = CardMigrationRecovery.class.getResourceAsStream("/db/migration/" + SCRIPT)) {
      require(stream != null, "Corrected V31 migration resource is missing.");
      String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\uFEFF", "");
      require(!sql.toLowerCase(Locale.ROOT).contains("idx_card_review_queue"), "Use the corrected V31 migration before recovery.");
      var checks = new LinkedHashMap<String, String>();
      var matcher = Pattern.compile("(?is)ALTER\\s+TABLE\\s+accounts\\s+ADD\\s+CONSTRAINT\\s+(\\w+)\\s+CHECK\\s*\\((.*?)\\)\\s*;").matcher(sql);
      while (matcher.find()) checks.put(matcher.group(1).toUpperCase(Locale.ROOT), condition(matcher.group(2)));
      require(checks.keySet().equals(Set.of("CK_CARD_CUSTOMER_BLOCK", "CK_LOCAL_CARD_APPLICATION")), "Corrected V31 constraints are not the expected definitions.");
      return new Definition(ChecksumCalculator.calculate(new StringResource(sql.stripTrailing() + "\n" + OLD_INDEX + "\n")),
          ChecksumCalculator.calculate(new StringResource(sql)), Map.copyOf(checks));
    } catch (IOException error) { throw new IllegalStateException("Cannot read the corrected V31 migration.", error); }
  }

  private static void owner(Connection connection, String schema) throws SQLException {
    require("Oracle".equals(connection.getMetaData().getDatabaseProductName()), "V31 recovery is only for Oracle.");
    var rows = rows(connection, "SELECT SYS_CONTEXT('USERENV','SESSION_USER') SESSION_OWNER,"
        + "SYS_CONTEXT('USERENV','CURRENT_SCHEMA') CURRENT_OWNER,SYS_CONTEXT('USERENV','CON_NAME') CONTAINER_NAME FROM dual");
    require(rows.size() == 1, "Cannot establish the Oracle recovery connection.");
    var row = rows.get(0);
    require(schema.equals(text(row, "SESSION_OWNER")) && schema.equals(text(row, "CURRENT_OWNER")),
        "Connect directly as the configured application schema owner.");
    String container = text(row, "CONTAINER_NAME");
    require(container != null && !container.isBlank() && !Set.of("CDB$ROOT", "PDB$SEED").contains(container.toUpperCase(Locale.ROOT)),
        "Connect to the application PDB, not the root or seed container.");
  }

  private static Snapshot snapshot(Connection connection, String schema) throws SQLException {
    return new Snapshot(history(connection, schema),
        rows(connection, "SELECT COLUMN_NAME,DATA_TYPE,CHAR_LENGTH,CHAR_USED,DATA_PRECISION,DATA_SCALE,NULLABLE,DATA_DEFAULT"
            + " FROM USER_TAB_COLUMNS WHERE TABLE_NAME='ACCOUNTS' AND COLUMN_NAME IN"
            + " ('CARD_REQUEST_ID','CARD_REQUEST_HASH','CARD_SLOT_KEY','CARD_CUSTOMER_BLOCK') ORDER BY COLUMN_NAME"),
        rows(connection, "SELECT i.INDEX_NAME,i.TABLE_NAME,i.INDEX_TYPE,i.UNIQUENESS,i.STATUS,i.VISIBILITY,i.PARTITIONED,"
            + "c.COLUMN_NAME,c.COLUMN_POSITION,c.DESCEND FROM USER_INDEXES i JOIN USER_IND_COLUMNS c"
            + " ON c.INDEX_NAME=i.INDEX_NAME AND c.TABLE_NAME=i.TABLE_NAME WHERE i.INDEX_NAME IN"
            + " ('UK_CARD_REQUEST','UK_CARD_SLOT','IDX_LOAN_REVIEW_QUEUE','IDX_CARD_REVIEW_QUEUE') ORDER BY i.INDEX_NAME,c.COLUMN_POSITION"),
        rows(connection, "SELECT CONSTRAINT_NAME,TABLE_NAME,CONSTRAINT_TYPE,STATUS,VALIDATED,DEFERRABLE,SEARCH_CONDITION_VC"
            + " FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME IN ('CK_CARD_CUSTOMER_BLOCK','CK_LOCAL_CARD_APPLICATION') ORDER BY CONSTRAINT_NAME"));
  }

  static History verify(Snapshot snapshot, Definition definition) {
    var history = snapshot.history();
    require(!history.isEmpty(), "Expected migration history is missing.");
    History target = history.get(history.size() - 1);
    require("31".equals(target.version()) && SCRIPT.equals(target.script()) && "SQL".equals(target.type())
        && "local card applications".equals(target.description()), "Only the latest original V31 card migration can be recovered.");
    require(Objects.equals(target.checksum(), target.success() ? definition.correctedChecksum() : definition.oldChecksum()),
        "The V31 checksum is not the known final-index failure (or already-corrected migration).");
    var versions = new java.util.HashSet<String>();
    int lastRank = -1;
    for (History row : history) {
      require(row.rank() > lastRank && row.version() != null && versions.add(row.version())
          && ("SQL".equals(row.type()) || originalJavaMigration(row)),
          "Duplicate, unordered, baseline, or unexpected migration history requires separate review.");
      lastRank = row.rank();
      require(MigrationVersion.fromVersion(row.version()).compareTo(MigrationVersion.fromVersion("31")) <= 0,
          "Later migrations require separate recovery review.");
      if (row != target) require(row.success() && (row.checksum() != null || originalJavaMigration(row)),
          "Another migration failed or lacks its expected checksum; recovery stopped.");
    }
    require(history.stream().anyMatch(r -> "1".equals(r.version()) && "V1__create_core_banking_schema.sql".equals(r.script()) && r.success())
        && history.stream().anyMatch(r -> "30".equals(r.version()) && "V30__authorized_scheduled_payments.sql".equals(r.script()) && r.success()),
        "The expected original project history through V30 is required.");
    verifyColumns(snapshot.columns());
    verifyIndexes(snapshot.indexes());
    require(snapshot.constraints().size() == 2, "Both V31 check constraints must already exist.");
    for (var c : snapshot.constraints()) {
      String name = text(c, "CONSTRAINT_NAME");
      require("ACCOUNTS".equals(text(c, "TABLE_NAME")) && "C".equals(text(c, "CONSTRAINT_TYPE"))
          && "ENABLED".equals(text(c, "STATUS")) && "VALIDATED".equals(text(c, "VALIDATED"))
          && "NOT DEFERRABLE".equals(text(c, "DEFERRABLE"))
          && Objects.equals(definition.checks().get(name), condition(text(c, "SEARCH_CONDITION_VC"))),
          "A V31 check constraint is missing, altered, disabled, or not validated: " + name);
    }
    require(snapshot.constraints().stream().map(c -> text(c, "CONSTRAINT_NAME")).collect(java.util.stream.Collectors.toSet())
        .equals(definition.checks().keySet()), "The V31 constraint names do not match.");
    return target;
  }

  private static void verifyColumns(List<Map<String, Object>> columns) {
    require(columns.size() == 4 && columns.stream().map(c -> text(c, "COLUMN_NAME")).collect(java.util.stream.Collectors.toSet()).equals(COLUMN_NAMES),
        "All four V31 card columns must already exist.");
    for (var c : columns) {
      String name = text(c, "COLUMN_NAME");
      boolean correct;
      if ("CARD_CUSTOMER_BLOCK".equals(name)) {
        correct = "NUMBER".equals(text(c, "DATA_TYPE")) && number(c, "DATA_PRECISION") == 1 && number(c, "DATA_SCALE") == 0
            && "N".equals(text(c, "NULLABLE")) && "0".equals(condition(text(c, "DATA_DEFAULT")));
      } else {
        int length = "CARD_REQUEST_ID".equals(name) ? 36 : "CARD_REQUEST_HASH".equals(name) ? 64 : 100;
        correct = "VARCHAR2".equals(text(c, "DATA_TYPE")) && number(c, "CHAR_LENGTH") == length
            && Set.of("B", "C").contains(Objects.toString(c.get("CHAR_USED"), "")) && "Y".equals(text(c, "NULLABLE"))
            && (text(c, "DATA_DEFAULT") == null || text(c, "DATA_DEFAULT").isBlank());
      }
      require(correct, "A V31 card column differs from the expected definition: " + name);
    }
  }

  private static void verifyIndexes(List<Map<String, Object>> indexes) {
    var grouped = new LinkedHashMap<String, List<String>>();
    for (var index : indexes) {
      String name = text(index, "INDEX_NAME");
      require(INDEX_NAMES.contains(name) && "ACCOUNTS".equals(text(index, "TABLE_NAME"))
          && "NORMAL".equals(text(index, "INDEX_TYPE")) && "VALID".equals(text(index, "STATUS"))
          && "VISIBLE".equals(text(index, "VISIBILITY")) && "NO".equals(text(index, "PARTITIONED"))
          && "ASC".equals(text(index, "DESCEND"))
          && (name.startsWith("UK_") ? "UNIQUE" : "NONUNIQUE").equals(text(index, "UNIQUENESS")),
          "A required V31/shared index is missing or has an unexpected definition: " + name);
      var columns = grouped.computeIfAbsent(name, ignored -> new ArrayList<>());
      require(number(index, "COLUMN_POSITION") == columns.size() + 1, "Unexpected index column ordering: " + name);
      columns.add(text(index, "COLUMN_NAME"));
    }
    require(grouped.equals(Map.of("UK_CARD_REQUEST", List.of("CARD_REQUEST_ID"), "UK_CARD_SLOT", List.of("CARD_SLOT_KEY"),
        "IDX_LOAN_REVIEW_QUEUE", List.of("ACCOUNT_TYPE", "PRODUCT_STATUS", "CREATED_AT"))),
        "Expected two card unique indexes and the existing shared review index.");
  }

  static void validate(Flyway flyway, List<History> history, History target, Definition definition) {
    var infos = flyway.info().all();
    // A teammate can still recover the known V31 failure after pulling newer, unapplied
    // migrations. Only genuinely pending versions after V31 may be absent from history.
    var pendingLater = Arrays.stream(infos).filter(info -> info.getVersion() != null
        && info.getVersion().compareTo(MigrationVersion.fromVersion("31")) > 0
        && info.getState() == MigrationState.PENDING).map(info -> info.getVersion().toString())
        .collect(java.util.stream.Collectors.toSet());
    require(infos.length - pendingLater.size() == history.size(), "Resolved and recorded migration counts differ; recovery stopped.");
    var resolved = new LinkedHashMap<String, MigrationInfo>();
    for (var info : infos) {
      require(info.getVersion() != null && resolved.put(info.getVersion().toString(), info) == null,
          "Duplicate or non-versioned resolved migration requires separate review.");
    }
    for (var row : history) {
      MigrationInfo info = resolved.get(row.version());
      // Flyway 12.4 leaves MigrationInfo.getResolvedDescription/Type at their null defaults.
      // Its ordinary getters prefer applied history, so inspect the actual resolved resource.
      require(info instanceof MigrationInfoImpl,
          "Flyway did not expose the resolved migration metadata required for recovery: V" + row.version());
      var resource = ((MigrationInfoImpl) info).getResolvedMigration();
      require(resource != null && row.script().equals(resource.getScript()) && row.description().equals(resource.getDescription())
          && resource.getType() != null && row.type().equals(resource.getType().name()),
          "A recorded migration is missing or differs from the original resolved script: V" + row.version());
      Integer expectedChecksum = row.checksum();
      if (row == target) expectedChecksum = definition.correctedChecksum();
      require(Objects.equals(resource.getChecksum(), expectedChecksum),
          "An earlier migration checksum differs, or the corrected V31 resource is not loaded: V" + row.version());
      require(info.getState() == (row.success() ? MigrationState.SUCCESS : MigrationState.FAILED),
          "Unexpected Flyway migration state: V" + row.version());
    }
    ValidateResult result = flyway.validateWithResult();
    require(result.invalidMigrations != null, "Flyway did not return validation details; recovery stopped.");
    // Standalone Flyway validation reports a resolved but not-yet-applied version as an
    // issue. Exclude only that exact issue for versions independently confirmed pending
    // after V31 above. Missing/changed/failed migrations and earlier gaps still stop here.
    var issues = result.invalidMigrations.stream().filter(issue -> !(pendingLater.contains(issue.version)
        && issue.errorDetails != null && issue.errorDetails.errorCode == CoreErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED)).toList();
    if (target.success()) {
      require(issues.isEmpty() && (result.validationSuccessful || !result.invalidMigrations.isEmpty()), "Already-corrected history did not pass full validation.");
    } else {
      require(!result.validationSuccessful && issues.size() == 1,
          "Flyway found an additional validation issue. V31 recovery stopped.");
      var issue = issues.get(0);
      require("31".equals(issue.version) && issue.errorDetails != null
          && issue.errorDetails.errorCode == CoreErrorCode.FAILED_VERSIONED_MIGRATION,
          "The only permitted validation issue is the known failed V31 migration.");
    }
  }

  private static List<History> history(Connection connection, String schema) throws SQLException {
    var result = new ArrayList<History>();
    String sql = "SELECT \"installed_rank\",\"version\",\"description\",\"type\",\"script\",\"checksum\",\"success\","
        + "\"installed_by\",\"installed_on\",\"execution_time\" FROM " + qualified(schema, HISTORY) + " ORDER BY \"installed_rank\"";
    try (var statement = connection.prepareStatement(sql)) {
      statement.setQueryTimeout(15);
      try (var rows = statement.executeQuery()) {
        while (rows.next()) {
          int checksum = rows.getInt(6);
          Integer nullableChecksum = rows.wasNull() ? null : checksum;
          boolean success = rows.getBoolean(7);
          require(!rows.wasNull(), "Migration history has a missing success flag.");
          result.add(new History(rows.getInt(1), rows.getString(2), rows.getString(3), rows.getString(4), rows.getString(5),
              nullableChecksum, success, rows.getString(8), rows.getString(9), rows.getInt(10)));
        }
      }
    }
    return List.copyOf(result);
  }

  private static List<Map<String, Object>> rows(Connection connection, String sql) throws SQLException {
    var result = new ArrayList<Map<String, Object>>();
    try (var statement = connection.prepareStatement(sql)) {
      statement.setQueryTimeout(15);
      try (var rows = statement.executeQuery()) {
        var metadata = rows.getMetaData();
        while (rows.next()) {
          var row = new LinkedHashMap<String, Object>();
          for (int i = 1; i <= metadata.getColumnCount(); i++) {
            Object value = rows.getObject(i);
            row.put(metadata.getColumnLabel(i).toUpperCase(Locale.ROOT), value == null ? null : value.toString());
          }
          result.add(java.util.Collections.unmodifiableMap(row));
        }
      }
    }
    return List.copyOf(result);
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (var statement = connection.createStatement()) { statement.setQueryTimeout(15); statement.execute(sql); }
  }
  private static String qualified(String schema, String table) { return "\"" + schema + "\".\"" + table + "\""; }
  private static String text(Map<String, Object> row, String key) { return Objects.toString(row.get(key), null); }
  private static int number(Map<String, Object> row, String key) {
    try { return new java.math.BigDecimal(text(row, key)).intValueExact(); }
    catch (Exception invalid) { return Integer.MIN_VALUE; }
  }
  static String condition(String sql) {
    if (sql == null) return null;
    var normalized = new StringBuilder();
    for (int i = 0; i < sql.length(); i++) {
      char c = sql.charAt(i);
      if (c == '\'') {
        normalized.append(c);
        boolean closed = false;
        while (++i < sql.length()) {
          char literal = sql.charAt(i);
          normalized.append(literal);
          if (literal == '\'') {
            if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') normalized.append(sql.charAt(++i));
            else { closed = true; break; }
          }
        }
        require(closed, "Unterminated SQL literal in a constraint definition.");
      } else if (c == '"') {
        var identifier = new StringBuilder();
        boolean closed = false;
        while (++i < sql.length()) {
          char part = sql.charAt(i);
          if (part == '"') {
            if (i + 1 < sql.length() && sql.charAt(i + 1) == '"') { identifier.append("\"\""); i++; }
            else { closed = true; break; }
          } else identifier.append(part);
        }
        require(closed, "Unterminated quoted identifier in a constraint definition.");
        String name = identifier.toString();
        if (name.matches("[A-Z_][A-Z0-9_$#]*")) normalized.append(name);
        else normalized.append('"').append(name).append('"');
      } else if (!Character.isWhitespace(c)) normalized.append(Character.toUpperCase(c));
    }
    String value = normalized.toString();
    while (value.startsWith("(") && value.endsWith(")")) {
      int depth = 0; boolean outer = true, quoted = false;
      for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        if (c == '\'') {
          if (quoted && i + 1 < value.length() && value.charAt(i + 1) == '\'') { i++; continue; }
          quoted = !quoted;
        }
        if (quoted) continue;
        if (c == '(') depth++;
        else if (c == ')') depth--;
        if (depth == 0 && i < value.length() - 1) { outer = false; break; }
      }
      if (!outer || depth != 0) break;
      value = value.substring(1, value.length() - 1);
    }
    return value;
  }
  private static boolean originalJavaMigration(History row) {
    // BaseJavaMigration intentionally has no checksum; match the one historical Java cutover exactly.
    return "17".equals(row.version()) && "JDBC".equals(row.type()) && row.checksum() == null
        && "db.migration.V17__migrate_banking_products".equals(row.script())
        && "migrate banking products".equals(row.description());
  }
  private static void require(boolean valid, String message) {
    if (!valid) throw new IllegalArgumentException(message);
  }
}
