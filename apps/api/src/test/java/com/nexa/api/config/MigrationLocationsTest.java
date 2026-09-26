package com.nexa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MigrationLocationsTest {
  private Connection database() throws SQLException {
    return DriverManager.getConnection("jdbc:h2:mem:migrations_" + UUID.randomUUID(), "sa", "");
  }

  private void history(Connection connection) throws SQLException {
    try (var statement = connection.createStatement()) {
      statement.execute("CREATE TABLE \"flyway_schema_history\""
          + " (\"version\" VARCHAR(50), \"script\" VARCHAR(200), \"success\" BOOLEAN)");
    }
  }

  private void row(Connection connection, String version, String script, Boolean success) throws SQLException {
    try (var statement = connection.prepareStatement(
        "INSERT INTO \"flyway_schema_history\" (\"version\", \"script\", \"success\") VALUES (?, ?, ?)")) {
      statement.setString(1, version);
      statement.setString(2, script);
      statement.setObject(3, success);
      statement.executeUpdate();
    }
  }

  private void seeds(Connection connection) throws SQLException {
    row(connection, "2", "V2__seed_local_demo_data.sql", true);
    row(connection, "4", "V4__enable_local_demo_login.sql", true);
    row(connection, "10", "V10__seed_banking_read_models.sql", true);
  }

  @Test
  void freshSchemaNeverSelectsDemoSeeds() throws Exception {
    try (var connection = database()) {
      assertThat(MigrationLocations.select(connection)).containsExactly("classpath:db/migration");
    }
  }

  @Test
  void emptyHistoryAndNonDemoHistoryRemainSeedFree() throws Exception {
    try (var connection = database()) {
      history(connection);
      assertThat(MigrationLocations.select(connection)).containsExactly("classpath:db/migration");
      row(connection, "1", "V1__init.sql", true);
      row(connection, null, "R__reference_data.sql", true);
      assertThat(MigrationLocations.select(connection)).containsExactly("classpath:db/migration");
    }
  }

  @Test
  void completeHistoricalSeedSetKeepsItsOriginalLocationsForChecksumValidation() throws Exception {
    try (var connection = database()) {
      history(connection);
      seeds(connection);
      assertThat(MigrationLocations.select(connection))
          .containsExactly("classpath:db/migration", "classpath:db/local-migration");
    }
  }

  @Test
  void partiallyAppliedSeedsStopInsteadOfRunningMissingDemoMigrations() throws Exception {
    try (var connection = database()) {
      history(connection);
      row(connection, "2", "V2__seed_local_demo_data.sql", true);
      assertThatThrownBy(() -> MigrationLocations.select(connection))
          .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("partial, failed or inconsistent");
      row(connection, "4", "V4__enable_local_demo_login.sql", true);
      assertThatThrownBy(() -> MigrationLocations.select(connection))
          .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("prevent adding demo data");
    }
  }

  @Test
  void failedOrMissingSuccessCannotEnableHistoricalSeeds() throws Exception {
    for (Boolean success : new Boolean[] {false, null}) {
      try (var connection = database()) {
        history(connection);
        row(connection, "2", "V2__seed_local_demo_data.sql", success);
        assertThatThrownBy(() -> MigrationLocations.select(connection))
            .isInstanceOf(IllegalArgumentException.class);
      }
    }
  }

  @Test
  void renamedScriptsAndRemappedSeedVersionsStop() throws Exception {
    for (String[] values : new String[][] {
        {"2", "V2__unexpected.sql"}, {"99", "V2__seed_local_demo_data.sql"}}) {
      try (var connection = database()) {
        history(connection);
        row(connection, values[0], values[1], true);
        assertThatThrownBy(() -> MigrationLocations.select(connection))
            .isInstanceOf(IllegalArgumentException.class);
      }
    }
  }

  @Test
  void duplicateSeedHistoryCannotEnableSeeds() throws Exception {
    try (var connection = database()) {
      history(connection);
      seeds(connection);
      row(connection, "2", "V2__seed_local_demo_data.sql", true);
      assertThatThrownBy(() -> MigrationLocations.select(connection))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void configuredSchemaIsCheckedWithoutUsingAnotherOwnersHistory() throws Exception {
    try (var connection = database()) {
      history(connection);
      seeds(connection);
      try (var statement = connection.createStatement()) {
        statement.execute("CREATE SCHEMA NEXA_FRESH");
      }
      assertThat(MigrationLocations.select(connection, "NEXA_FRESH"))
          .containsExactly("classpath:db/migration");
      assertThat(MigrationLocations.select(connection, "PUBLIC"))
          .containsExactly("classpath:db/migration", "classpath:db/local-migration");
    }
  }

  @Test
  void referenceProjectHistoryIsRejectedBeforeInterpretingItsOnboardingVersionsAsDemoSeeds()
      throws Exception {
    try (var connection = database()) {
      history(connection);
      // Deliberately insert V1 last: migration-table row order is not guaranteed.
      row(connection, "4", "V4__in_person_identity_review.sql", true);
      row(connection, "3", "V3__application_request_audit.sql", true);
      row(connection, "2", "V2__manual_account_applications.sql", true);
      row(connection, "1", "V1__initialize_nexa_schema.sql", true);

      assertThatThrownBy(() -> MigrationLocations.select(connection))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("separate Nexa reference project's migration history")
          .hasMessageContaining("V1__create_core_banking_schema.sql")
          .hasMessageContaining("NEXA_BANK_APP")
          .hasMessageContaining("Do not repair, reset or drop");

      try (var statement = connection.createStatement()) {
        statement.execute("CREATE SCHEMA NEXA_BANK_APP");
      }
      assertThat(MigrationLocations.select(connection, "NEXA_BANK_APP"))
          .containsExactly("classpath:db/migration");
      try (var statement = connection.createStatement();
          var rows = statement.executeQuery("SELECT COUNT(*) FROM \"flyway_schema_history\"")) {
        rows.next();
        assertThat(rows.getInt(1)).isEqualTo(4);
      }
    }
  }

  @Test
  void referenceInitialMigrationAloneIsAlreadyAnIncompatibleHistory() throws Exception {
    try (var connection = database()) {
      history(connection);
      row(connection, "1", "V1__initialize_nexa_schema.sql", true);
      assertThatThrownBy(() -> MigrationLocations.select(connection))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Use a separate dedicated schema")
          .hasMessageContaining("Migration stopped before applying changes");
    }
  }
}
