package com.nexa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.internal.info.MigrationInfoImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real Flyway resolution/validation over synthetic H2 history; no Oracle connection or migration execution. */
class CardMigrationRecoveryFlywayTest {
  private Connection database;
  private Flyway flyway;
  private CardMigrationRecovery.Definition definition;

  @BeforeEach void setup() throws Exception {
    String url = "jdbc:h2:mem:card-recovery-real-flyway-" + UUID.randomUUID();
    database = DriverManager.getConnection(url, "sa", "");
    assertThat(database.getMetaData().getURL()).startsWith("jdbc:h2:mem:card-recovery-real-flyway-");
    flyway = Flyway.configure().dataSource(url, "sa", "").defaultSchema("PUBLIC")
        .locations("classpath:db/migration").cleanDisabled(true).load();
    definition = CardMigrationRecovery.definition();
    // Resolve the actual packaged SQL and Java migrations. Seed history only: historical Oracle
    // cutover SQL must not be run or rewritten to make this metadata regression pass on H2.
    MigrationInfo[] migrations = flyway.info().all();
    assertThat(migrations).isNotEmpty();
    assertThat(Arrays.stream(migrations).map(migration -> migration.getVersion().toString())).contains("31", "32");
    try (var statement = database.createStatement()) {
      statement.execute("CREATE TABLE \"flyway_schema_history\" (\"installed_rank\" INT PRIMARY KEY,"
          + "\"version\" VARCHAR(50),\"description\" VARCHAR(200) NOT NULL,\"type\" VARCHAR(20) NOT NULL,"
          + "\"script\" VARCHAR(1000) NOT NULL,\"checksum\" INT,\"installed_by\" VARCHAR(100) NOT NULL,"
          + "\"installed_on\" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,\"execution_time\" INT NOT NULL,"
          + "\"success\" BOOLEAN NOT NULL)");
    }
    int rank = 0;
    for (MigrationInfo migration : migrations) {
      if (migration.getVersion().compareTo(MigrationVersion.fromVersion("31")) > 0) continue;
      boolean failedCardMigration = migration.getVersion().toString().equals("31");
      try (var statement = database.prepareStatement("INSERT INTO \"flyway_schema_history\" VALUES(?,?,?,?,?,?,?, ?,?,?)")) {
        statement.setInt(1, ++rank);
        statement.setString(2, migration.getVersion().toString());
        statement.setString(3, migration.getDescription());
        statement.setString(4, migration.getType().name());
        statement.setString(5, migration.getScript());
        Integer checksum = migration.getChecksum();
        if (failedCardMigration) checksum = definition.oldChecksum();
        statement.setObject(6, checksum);
        statement.setString(7, "SYNTHETIC_FIXTURE");
        statement.setTimestamp(8, Timestamp.valueOf("2026-09-27 10:00:00"));
        statement.setInt(9, 10);
        statement.setBoolean(10, !failedCardMigration);
        statement.executeUpdate();
      }
    }
  }

  @AfterEach void close() throws Exception { database.close(); }

  @Test void laterUnappliedFundingMigrationDoesNotBlockKnownV31Recovery() throws Exception {
    assertThat(info("32").getState()).isEqualTo(MigrationState.PENDING);
    var before = history();
    assertThatCode(() -> validate(flyway)).doesNotThrowAnyException();
    assertThat(history()).isEqualTo(before);
    update("UPDATE \"flyway_schema_history\" SET \"success\"=TRUE,\"checksum\"=? WHERE \"version\"='31'",
        definition.correctedChecksum());
    assertThatCode(() -> validate(flyway)).doesNotThrowAnyException();
    assertThat(info("32").getState()).isEqualTo(MigrationState.PENDING);
    assertThat(history()).noneMatch(row -> row.version().equals("32"));
  }

  @Test void missingEarlierHistoryCannotBeHiddenAsAnUnappliedMigration() throws Exception {
    update("DELETE FROM \"flyway_schema_history\" WHERE \"version\"='30'");
    assertThatThrownBy(() -> validate(flyway)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("migration counts differ");
  }

  @Test void realFlywayNullResolvedConvenienceMetadataDoesNotRejectKnownFailedV31() throws Exception {
    MigrationInfo first = info("1");
    assertThat(first).isInstanceOf(MigrationInfoImpl.class);
    // Flyway 12.4 MigrationInfoImpl inherits these null interface defaults. They are not
    // evidence that the resource is missing: its actual ResolvedMigration is present.
    assertThat(first.getResolvedDescription()).isNull();
    assertThat(first.getResolvedType()).isNull();
    var resolved = ((MigrationInfoImpl) first).getResolvedMigration();
    assertThat(resolved).isNotNull();
    assertThat(resolved.getScript()).isEqualTo("V1__create_core_banking_schema.sql");
    assertThat(resolved.getDescription()).isEqualTo("create core banking schema");
    assertThat(resolved.getType()).isEqualTo(CoreMigrationType.SQL);
    assertThat(first.getResolvedChecksum()).isNotNull().isEqualTo(first.getAppliedChecksum());
    assertOriginalJavaMigration();
    assertThat(info("31").getState()).isEqualTo(MigrationState.FAILED);
    var validation = flyway.validateWithResult();
    assertThat(validation.validationSuccessful).isFalse();
    assertThat(validation.invalidMigrations).filteredOn(issue -> "31".equals(issue.version)).singleElement().satisfies(issue -> {
      assertThat(issue.errorDetails.errorCode).isEqualTo(CoreErrorCode.FAILED_VERSIONED_MIGRATION);
    });
    assertThat(validation.invalidMigrations).filteredOn(issue -> "32".equals(issue.version)).singleElement().satisfies(issue -> {
      assertThat(issue.errorDetails.errorCode).isEqualTo(CoreErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED);
    });
    var before = history();
    assertThatCode(() -> validate(flyway)).doesNotThrowAnyException();
    assertThat(history()).isEqualTo(before);
  }

  @Test void realFlywayAcceptsAlreadyCorrectedHistoryAndOriginalChecksumlessJavaV17() throws Exception {
    update("UPDATE \"flyway_schema_history\" SET \"success\"=TRUE,\"checksum\"=? WHERE \"version\"='31'",
        definition.correctedChecksum());
    assertOriginalJavaMigration();
    assertThat(flyway.validateWithResult().invalidMigrations).allSatisfy(issue -> {
      assertThat(MigrationVersion.fromVersion(issue.version)).isGreaterThan(MigrationVersion.fromVersion("31"));
      assertThat(issue.errorDetails.errorCode).isEqualTo(CoreErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED);
    });
    var before = history();
    assertThatCode(() -> validate(flyway)).doesNotThrowAnyException();
    assertThat(history()).isEqualTo(before);
  }

  @Test void actualPriorChecksumDriftStillStopsRecoveryWhenV31IsTheKnownFailure() throws Exception {
    update("UPDATE \"flyway_schema_history\" SET \"checksum\"=\"checksum\"+1 WHERE \"version\"='1'");
    assertThat(info("1").getAppliedChecksum()).isNotEqualTo(info("1").getResolvedChecksum());
    assertThat(flyway.validateWithResult().invalidMigrations).anySatisfy(issue -> {
      assertThat(issue.version).isEqualTo("1");
      assertThat(issue.errorDetails.errorCode).isEqualTo(CoreErrorCode.CHECKSUM_MISMATCH);
    });
    assertThatThrownBy(() -> validate(flyway)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("checksum").hasMessageContaining("V1");
  }

  @Test void missingSqlChecksumAndInventedJavaChecksumAreNotAcceptedAsNullMetadataFallbacks() throws Exception {
    Integer original = info("1").getAppliedChecksum();
    update("UPDATE \"flyway_schema_history\" SET \"checksum\"=NULL WHERE \"version\"='1'");
    assertThatThrownBy(() -> validate(flyway)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("checksum").hasMessageContaining("V1");
    update("UPDATE \"flyway_schema_history\" SET \"checksum\"=? WHERE \"version\"='1'", original);
    update("UPDATE \"flyway_schema_history\" SET \"checksum\"=17 WHERE \"version\"='17'");
    assertThatThrownBy(() -> validate(flyway)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("checksum").hasMessageContaining("V17");
  }

  @Test void changedAppliedScriptDescriptionOrTypeCannotMasqueradeAsResolvedMetadata() throws Exception {
    for (String column : List.of("script", "description", "type")) {
      MigrationInfo original = info("1");
      String previous = switch (column) {
        case "script" -> original.getScript();
        case "description" -> original.getDescription();
        default -> original.getType().name();
      };
      String changed = column.equals("type") ? "JDBC" : "different-original-migration";
      String sql = "UPDATE \"flyway_schema_history\" SET \"" + column + "\"=? WHERE \"version\"='1'";
      update(sql, changed);
      assertThatThrownBy(() -> validate(flyway)).as("Changed applied %s must stop recovery", column)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(column.equals("type") ? "migration counts differ" : "V1");
      update(sql, previous);
    }
  }

  @Test void appliedHistoryWithoutResolvedResourcesIsRejectedDespiteEqualRecordedFields() throws Exception {
    Flyway missing = Flyway.configure().dataSource(database.getMetaData().getURL(), "sa", "")
        .defaultSchema("PUBLIC").locations("classpath:synthetic-absent-recovery-resources")
        .cleanDisabled(true).load();
    var first = Arrays.stream(missing.info().all()).filter(item -> "1".equals(item.getVersion().toString()))
        .findFirst().orElseThrow();
    assertThat(first.getScript()).isEqualTo("V1__create_core_banking_schema.sql");
    assertThat(((MigrationInfoImpl) first).getResolvedMigration()).isNull();
    assertThatThrownBy(() -> validate(missing)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("V1");
  }

  private void assertOriginalJavaMigration() {
    MigrationInfo java = info("17");
    assertThat(java.getType()).isEqualTo(CoreMigrationType.JDBC);
    assertThat(java.getScript()).isEqualTo("db.migration.V17__migrate_banking_products");
    assertThat(java.getAppliedChecksum()).isNull();
    assertThat(java.getResolvedChecksum()).isNull();
    assertThat(((MigrationInfoImpl) java).getResolvedMigration()).isNotNull();
    assertThat(java.getState()).isEqualTo(MigrationState.SUCCESS);
  }

  private MigrationInfo info(String version) {
    return Arrays.stream(flyway.info().all()).filter(item -> version.equals(item.getVersion().toString()))
        .findFirst().orElseThrow();
  }

  private void validate(Flyway candidate) throws Exception {
    var rows = history();
    CardMigrationRecovery.validate(candidate, rows, rows.get(rows.size() - 1), definition);
  }

  private void update(String sql, Object... parameters) throws Exception {
    try (var statement = database.prepareStatement(sql)) {
      for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
      assertThat(statement.executeUpdate()).isEqualTo(1);
    }
  }

  private List<CardMigrationRecovery.History> history() throws Exception {
    var result = new ArrayList<CardMigrationRecovery.History>();
    try (var statement = database.createStatement(); var rows = statement.executeQuery(
        "SELECT \"installed_rank\",\"version\",\"description\",\"type\",\"script\",\"checksum\",\"success\","
            + "\"installed_by\",\"installed_on\",\"execution_time\" FROM \"flyway_schema_history\" ORDER BY \"installed_rank\"")) {
      while (rows.next()) result.add(new CardMigrationRecovery.History(rows.getInt(1), rows.getString(2), rows.getString(3),
          rows.getString(4), rows.getString(5), rows.getObject(6, Integer.class), rows.getBoolean(7), rows.getString(8),
          rows.getString(9), rows.getInt(10)));
    }
    return List.copyOf(result);
  }
}
