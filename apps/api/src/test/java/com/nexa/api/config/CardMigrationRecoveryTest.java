package com.nexa.api.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.ErrorDetails;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.output.ValidateOutput;
import org.flywaydb.core.api.output.ValidateResult;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.flywaydb.core.internal.info.MigrationInfoImpl;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;
import org.flywaydb.core.internal.resource.StringResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Synthetic Oracle metadata over an actual H2 transaction/history table; no Oracle connection or DDL certification. */
class CardMigrationRecoveryTest {
  private static final String SCHEMA = "NEXA_RECOVERY";
  private Connection db, connection;
  private Flyway flyway;
  private CardMigrationRecovery.Definition definition;
  private final List<String> locks = new ArrayList<>(), writes = new ArrayList<>();
  private boolean casMiss, changeUnderLock, priorChecksumMismatch, invalidValidation, nonOracle;
  private String owner = SCHEMA, pdb = "FREEPDB1";
  private int commits, rollbacks;

  @BeforeEach void setup() throws Exception {
    definition = CardMigrationRecovery.definition();
    db = DriverManager.getConnection("jdbc:h2:mem:card-recovery-" + UUID.randomUUID() + ";MODE=Oracle", "sa", "");
    sql("CREATE SCHEMA " + SCHEMA);
    db.setSchema(SCHEMA);
    sql("CREATE TABLE \"flyway_schema_history\" (\"installed_rank\" INT PRIMARY KEY,\"version\" VARCHAR(30),\"description\" VARCHAR(200),"
        + "\"type\" VARCHAR(30),\"script\" VARCHAR(250),\"checksum\" INT,\"success\" INT,\"installed_by\" VARCHAR(30),"
        + "\"installed_on\" TIMESTAMP,\"execution_time\" INT)");
    history(1, "1", "create core banking schema", "SQL", "V1__create_core_banking_schema.sql", 111, true);
    history(2, "17", "migrate banking products", "JDBC", "db.migration.V17__migrate_banking_products", null, true);
    history(3, "30", "authorized scheduled payments", "SQL", "V30__authorized_scheduled_payments.sql", 3030, true);
    history(4, "31", "local card applications", "SQL", CardMigrationRecovery.SCRIPT, definition.oldChecksum(), false);
    sql("CREATE TABLE accounts(id INT,balance DECIMAL(19,2))");
    sql("INSERT INTO accounts VALUES(1,1234.56)");
    sql("CREATE TABLE USER_TAB_COLUMNS(TABLE_NAME VARCHAR(30),COLUMN_NAME VARCHAR(30),DATA_TYPE VARCHAR(30),CHAR_LENGTH INT,"
        + "CHAR_USED VARCHAR(1),DATA_PRECISION INT,DATA_SCALE INT,NULLABLE VARCHAR(1),DATA_DEFAULT VARCHAR(2000))");
    sql("INSERT INTO USER_TAB_COLUMNS VALUES('ACCOUNTS','CARD_REQUEST_ID','VARCHAR2',36,'B',NULL,NULL,'Y',NULL)");
    sql("INSERT INTO USER_TAB_COLUMNS VALUES('ACCOUNTS','CARD_REQUEST_HASH','VARCHAR2',64,'B',NULL,NULL,'Y',NULL)");
    sql("INSERT INTO USER_TAB_COLUMNS VALUES('ACCOUNTS','CARD_SLOT_KEY','VARCHAR2',100,'B',NULL,NULL,'Y',NULL)");
    sql("INSERT INTO USER_TAB_COLUMNS VALUES('ACCOUNTS','CARD_CUSTOMER_BLOCK','NUMBER',0,NULL,1,0,'N','0')");
    sql("CREATE TABLE USER_INDEXES(INDEX_NAME VARCHAR(40),TABLE_NAME VARCHAR(30),INDEX_TYPE VARCHAR(30),UNIQUENESS VARCHAR(20),"
        + "STATUS VARCHAR(20),VISIBILITY VARCHAR(20),PARTITIONED VARCHAR(10))");
    sql("CREATE TABLE USER_IND_COLUMNS(INDEX_NAME VARCHAR(40),TABLE_NAME VARCHAR(30),COLUMN_NAME VARCHAR(40),COLUMN_POSITION INT,DESCEND VARCHAR(10))");
    index("UK_CARD_REQUEST", "UNIQUE", List.of("CARD_REQUEST_ID"));
    index("UK_CARD_SLOT", "UNIQUE", List.of("CARD_SLOT_KEY"));
    index("IDX_LOAN_REVIEW_QUEUE", "NONUNIQUE", List.of("ACCOUNT_TYPE", "PRODUCT_STATUS", "CREATED_AT"));
    sql("CREATE TABLE USER_CONSTRAINTS(CONSTRAINT_NAME VARCHAR(40),TABLE_NAME VARCHAR(30),CONSTRAINT_TYPE VARCHAR(1),"
        + "STATUS VARCHAR(20),VALIDATED VARCHAR(20),DEFERRABLE VARCHAR(20),SEARCH_CONDITION_VC VARCHAR(4000))");
    for (var check : definition.checks().entrySet()) {
      try (var statement = db.prepareStatement("INSERT INTO USER_CONSTRAINTS VALUES(?,'ACCOUNTS','C','ENABLED','VALIDATED','NOT DEFERRABLE',?)")) {
        statement.setString(1, check.getKey()); statement.setString(2, check.getValue()); statement.executeUpdate();
      }
    }
    connection = adapter();
    flyway = mock(Flyway.class);
    Configuration config = mock(Configuration.class);
    when(flyway.getConfiguration()).thenReturn(config);
    when(config.getDefaultSchema()).thenReturn(SCHEMA);
    when(config.getLocations()).thenReturn(new Location[] {new Location("classpath:db/migration")});
    when(config.getCallbacks()).thenReturn(new Callback[0]);
    MigrationInfoService info = mock(MigrationInfoService.class);
    when(flyway.info()).thenReturn(info);
    when(info.all()).thenAnswer(ignored -> resolvedInfo());
    when(flyway.validateWithResult()).thenAnswer(ignored -> validation());
  }
  @AfterEach void close() throws Exception { db.close(); }

  @Test void inspectAcceptsOriginalJavaV17WithoutChecksumAndNeverWritesOrLocks() throws Exception {
    CardMigrationRecovery.run(connection, flyway, SCHEMA, false);
    assertThat(writes).isEmpty(); assertThat(locks).isEmpty();
    assertThat(commits).isZero(); assertThat(rollbacks).isZero();
    assertThat(currentSuccess()).isFalse();
  }

  @Test void applyChangesOnlyKnownFailedHistoryChecksumAndSuccessThenReplayIsReadOnly() throws Exception {
    var original = historyRows();
    CardMigrationRecovery.run(connection, flyway, SCHEMA, true);
    assertThat(currentSuccess()).isTrue();
    assertThat(currentChecksum()).isEqualTo(definition.correctedChecksum());
    assertThat(writes).hasSize(1);
    assertThat(writes.get(0)).contains("SET \"success\"=1,\"checksum\"=?", "\"installed_rank\"=?", "\"version\"='31'", "\"success\"=0", "\"checksum\"=?")
        .doesNotContain("DELETE", "DROP", "ALTER", "accounts");
    assertThat(locks).containsExactly("LOCK TABLE \"NEXA_RECOVERY\".\"flyway_schema_history\" IN EXCLUSIVE MODE NOWAIT",
        "LOCK TABLE \"NEXA_RECOVERY\".\"ACCOUNTS\" IN EXCLUSIVE MODE NOWAIT");
    assertThat(commits).isEqualTo(1); assertThat(rollbacks).isZero();
    assertThat(historyRows().subList(0, 3)).isEqualTo(original.subList(0, 3));
    assertThat(accountBalance()).isEqualByComparingTo("1234.56");
    CardMigrationRecovery.run(connection, flyway, SCHEMA, true);
    assertThat(writes).hasSize(1); assertThat(commits).isEqualTo(1);
  }

  @Test void compareAndSetMissRollsBackWithoutLosingFailedHistory() throws Exception {
    casMiss = true;
    assertThatThrownBy(() -> CardMigrationRecovery.run(connection, flyway, SCHEMA, true))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("failed V31 row changed");
    assertThat(currentSuccess()).isFalse(); assertThat(currentChecksum()).isEqualTo(definition.oldChecksum());
    assertThat(commits).isZero(); assertThat(rollbacks).isEqualTo(1); assertThat(connection.getAutoCommit()).isTrue();
  }

  @Test void changedSchemaAfterLocksRollsBackBeforeHistoryUpdate() throws Exception {
    changeUnderLock = true;
    assertThatThrownBy(() -> CardMigrationRecovery.run(connection, flyway, SCHEMA, true))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("changed after inspection");
    assertThat(writes).isEmpty(); assertThat(commits).isZero(); assertThat(rollbacks).isEqualTo(1);
    assertThat(currentSuccess()).isFalse();
  }

  @Test void inheritedTransactionIsNeverCommittedByRecovery() throws Exception {
    db.setAutoCommit(false);
    assertThatThrownBy(() -> CardMigrationRecovery.run(connection, flyway, SCHEMA, true))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("existing transaction");
    assertThat(writes).isEmpty(); assertThat(locks).isEmpty(); assertThat(commits).isZero();
    assertThat(db.getAutoCommit()).isFalse(); db.rollback();
  }

  @Test void wrongOwnerRootPdbAndNonOracleAreRefusedBeforeMutation() throws Exception {
    owner = "SYSTEM";
    assertRefused("schema owner"); owner = SCHEMA;
    pdb = "CDB$ROOT"; assertRefused("application PDB"); pdb = "FREEPDB1";
    nonOracle = true; assertRefused("only for Oracle");
    assertThat(writes).isEmpty(); assertThat(locks).isEmpty();
  }

  @Test void unknownFailedChecksumOrSecondFailureCannotBeReconciled() throws Exception {
    sql("UPDATE \"flyway_schema_history\" SET \"checksum\"=123 WHERE \"version\"='31'");
    assertRefused("known final-index failure");
    sql("UPDATE \"flyway_schema_history\" SET \"checksum\"=" + definition.oldChecksum() + " WHERE \"version\"='31'");
    sql("UPDATE \"flyway_schema_history\" SET \"success\"=0 WHERE \"version\"='30'");
    assertRefused("Another migration failed"); assertThat(writes).isEmpty();
  }

  @Test void originalJavaMigrationCannotBeRenamedOrHaveInventedChecksum() throws Exception {
    sql("UPDATE \"flyway_schema_history\" SET \"script\"='db.migration.Other' WHERE \"version\"='17'");
    assertRefused("unexpected migration history");
    sql("UPDATE \"flyway_schema_history\" SET \"script\"='db.migration.V17__migrate_banking_products',\"checksum\"=17 WHERE \"version\"='17'");
    assertRefused("unexpected migration history"); assertThat(writes).isEmpty();
  }

  @Test void priorResolvedChecksumDifferenceAndAdditionalFlywayErrorAreRefused() throws Exception {
    priorChecksumMismatch = true; assertRefused("earlier migration checksum");
    priorChecksumMismatch = false; invalidValidation = true; assertRefused("additional validation issue");
    assertThat(writes).isEmpty();
  }

  @Test void incompleteColumnsAndChangedUniqueIndexAreRefused() throws Exception {
    sql("UPDATE USER_TAB_COLUMNS SET CHAR_LENGTH=35 WHERE COLUMN_NAME='CARD_REQUEST_ID'");
    assertRefused("card column differs");
    sql("UPDATE USER_TAB_COLUMNS SET CHAR_LENGTH=36 WHERE COLUMN_NAME='CARD_REQUEST_ID'");
    sql("UPDATE USER_INDEXES SET UNIQUENESS='NONUNIQUE' WHERE INDEX_NAME='UK_CARD_SLOT'");
    assertRefused("unexpected definition"); assertThat(writes).isEmpty();
  }

  @Test void disabledChecksAndChangedLiteralCaseOrSpacesCannotPassExactConstraintCheck() throws Exception {
    sql("UPDATE USER_CONSTRAINTS SET VALIDATED='NOT VALIDATED' WHERE CONSTRAINT_NAME='CK_LOCAL_CARD_APPLICATION'");
    assertRefused("not validated");
    sql("UPDATE USER_CONSTRAINTS SET VALIDATED='VALIDATED' WHERE CONSTRAINT_NAME='CK_LOCAL_CARD_APPLICATION'");
    String original = definition.checks().get("CK_LOCAL_CARD_APPLICATION");
    for (String value : List.of("'inr'", "'I N R'")) {
      try (var statement = db.prepareStatement("UPDATE USER_CONSTRAINTS SET SEARCH_CONDITION_VC=? WHERE CONSTRAINT_NAME='CK_LOCAL_CARD_APPLICATION'")) {
        statement.setString(1, original.replace("'INR'", value)); statement.executeUpdate();
      }
      assertRefused("constraint is missing, altered");
    }
    assertThat(writes).isEmpty();
  }

  @Test void constraintNormalizationPreservesSqlLiteralsAndQuotedLowercaseIdentifiers() {
    assertThat(CardMigrationRecovery.condition(" (( \"CURRENCY_CODE\" = 'INR' )) ")).isEqualTo("CURRENCY_CODE='INR'");
    assertThat(CardMigrationRecovery.condition("x = 'I N R' AND y='it''s' ")).isEqualTo("X='I N R'ANDY='it''s'");
    assertThat(CardMigrationRecovery.condition("\"currency_code\" = 'inr' ")).isNotEqualTo("CURRENCY_CODE='INR'");
  }

  @Test void originalChecksumUsesFlywayLineEndingRules() throws Exception {
    String sql;
    try (var stream = getClass().getResourceAsStream("/db/migration/" + CardMigrationRecovery.SCRIPT)) {
      sql = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
    String old = sql.stripTrailing() + "\n" + CardMigrationRecovery.OLD_INDEX + "\n";
    assertThat(ChecksumCalculator.calculate(new StringResource(old.replace("\n", "\r\n")))).isEqualTo(definition.oldChecksum());
    assertThat(definition.oldChecksum()).isNotEqualTo(definition.correctedChecksum());
  }

  private void assertRefused(String detail) {
    assertThatThrownBy(() -> CardMigrationRecovery.run(connection, flyway, SCHEMA, true))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(detail);
  }
  private void history(int rank, String version, String description, String type, String script, Integer checksum, boolean success) throws Exception {
    try (var statement = db.prepareStatement("INSERT INTO \"flyway_schema_history\" VALUES(?,?,?,?,?,?,?,'NEXA_RECOVERY',TIMESTAMP '2026-09-27 10:00:00',25)")) {
      statement.setInt(1, rank); statement.setString(2, version); statement.setString(3, description); statement.setString(4, type);
      statement.setString(5, script); statement.setObject(6, checksum); statement.setInt(7, success ? 1 : 0); statement.executeUpdate();
    }
  }
  private void index(String name, String uniqueness, List<String> columns) throws Exception {
    sql("INSERT INTO USER_INDEXES VALUES('" + name + "','ACCOUNTS','NORMAL','" + uniqueness + "','VALID','VISIBLE','NO')");
    for (int i = 0; i < columns.size(); i++) sql("INSERT INTO USER_IND_COLUMNS VALUES('" + name + "','ACCOUNTS','" + columns.get(i) + "'," + (i + 1) + ",'ASC')");
  }
  private void sql(String sql) throws Exception { try (var statement = db.createStatement()) { statement.execute(sql); } }
  private List<List<Object>> historyRows() throws Exception {
    var result = new ArrayList<List<Object>>();
    try (var statement = db.createStatement(); var rows = statement.executeQuery("SELECT * FROM \"flyway_schema_history\" ORDER BY \"installed_rank\"")) {
      while (rows.next()) {
        var row = new ArrayList<Object>(); for (int i = 1; i <= 10; i++) row.add(rows.getObject(i)); result.add(row);
      }
    }
    return result;
  }
  private boolean currentSuccess() throws Exception { return ((Number) historyRows().get(3).get(6)).intValue() == 1; }
  private int currentChecksum() throws Exception { return ((Number) historyRows().get(3).get(5)).intValue(); }
  private java.math.BigDecimal accountBalance() throws Exception {
    try (var statement = db.createStatement(); var rows = statement.executeQuery("SELECT balance FROM accounts WHERE id=1")) { rows.next(); return rows.getBigDecimal(1); }
  }
  private MigrationInfo[] resolvedInfo() throws Exception {
    var infos = new ArrayList<MigrationInfo>();
    for (var row : historyRows()) {
      String version = row.get(1).toString();
      MigrationInfoImpl info = mock(MigrationInfoImpl.class);
      ResolvedMigration resource = mock(ResolvedMigration.class);
      when(info.getResolvedMigration()).thenReturn(resource);
      when(info.getVersion()).thenReturn(MigrationVersion.fromVersion(version));
      when(info.getScript()).thenReturn(row.get(4).toString());
      when(resource.getScript()).thenReturn(row.get(4).toString());
      when(resource.getDescription()).thenReturn(row.get(2).toString());
      when(resource.getType()).thenReturn("17".equals(version) ? CoreMigrationType.JDBC : CoreMigrationType.SQL);
      Integer checksum = null;
      if ("31".equals(version)) checksum = definition.correctedChecksum();
      else if (!"17".equals(version)) checksum = ((Number) row.get(5)).intValue();
      if (priorChecksumMismatch && "30".equals(version)) checksum++;
      when(resource.getChecksum()).thenReturn(checksum);
      when(info.getState()).thenReturn(((Number) row.get(6)).intValue() == 1 ? MigrationState.SUCCESS : MigrationState.FAILED);
      infos.add(info);
    }
    return infos.toArray(MigrationInfo[]::new);
  }
  private ValidateResult validation() throws Exception {
    var issues = new ArrayList<ValidateOutput>();
    if (!currentSuccess()) issues.add(new ValidateOutput("31", "local card applications", CardMigrationRecovery.SCRIPT,
        new ErrorDetails(CoreErrorCode.FAILED_VERSIONED_MIGRATION, "Synthetic known failure")));
    if (invalidValidation) issues.add(new ValidateOutput("30", "authorized scheduled payments", "V30__authorized_scheduled_payments.sql",
        new ErrorDetails(CoreErrorCode.CHECKSUM_MISMATCH, "Synthetic prior mismatch")));
    return new ValidateResult("12.4.0", "isolated-h2-oracle-metadata", issues.isEmpty() ? null : new ErrorDetails(CoreErrorCode.VALIDATE_ERROR, "Synthetic validation"),
        issues.isEmpty(), 4, issues, List.of());
  }
  private Connection adapter() throws Exception {
    DatabaseMetaData actualMetadata = db.getMetaData();
    DatabaseMetaData metadata = (DatabaseMetaData) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class}, (p, method, args) -> {
      if (method.getName().equals("getDatabaseProductName")) return nonOracle ? "H2" : "Oracle";
      return invoke(method, actualMetadata, args);
    });
    return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (p, method, args) -> {
      if (method.getName().equals("getMetaData")) return metadata;
      if (method.getName().equals("commit")) commits++;
      if (method.getName().equals("rollback")) rollbacks++;
      if (method.getName().equals("createStatement")) {
        Statement actual = db.createStatement();
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Statement.class}, (statement, operation, values) -> {
          if (operation.getName().equals("execute") && values[0].toString().startsWith("LOCK TABLE ")) {
            locks.add(values[0].toString());
            if (changeUnderLock && locks.size() == 2) sql("UPDATE USER_TAB_COLUMNS SET DATA_DEFAULT='1' WHERE COLUMN_NAME='CARD_CUSTOMER_BLOCK'");
            return false;
          }
          return invoke(operation, actual, values);
        });
      }
      if (method.getName().equals("prepareStatement")) {
        String sql = args[0].toString();
        if (sql.startsWith("SELECT SYS_CONTEXT")) return db.prepareStatement("SELECT '" + owner + "' SESSION_OWNER,'" + SCHEMA + "' CURRENT_OWNER,'" + pdb + "' CONTAINER_NAME FROM dual");
        PreparedStatement actual = db.prepareStatement(sql);
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (statement, operation, values) -> {
          if (operation.getName().equals("executeUpdate")) { writes.add(sql); if (casMiss) return 0; }
          return invoke(operation, actual, values);
        });
      }
      return invoke(method, db, args);
    });
  }
  private static Object invoke(java.lang.reflect.Method method, Object target, Object[] args) throws Throwable {
    try { return method.invoke(target, args); }
    catch (InvocationTargetException wrapped) { throw wrapped.getCause(); }
  }
}
