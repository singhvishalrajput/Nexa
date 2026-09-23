package com.nexa.api.banking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class MigrationVersionTest {
  @Test
  void allPackagedMigrationsHaveUniqueVersions() {
    // Resolve every SQL/Java migration just as startup does, without applying Oracle DDL.
    var migrations = Flyway.configure()
        .dataSource("jdbc:h2:mem:migration_versions;MODE=Oracle", "sa", "")
        .locations("classpath:db/migration")
        .load().info().all();
    assertThat(Arrays.stream(migrations).map(m -> m.getVersion().toString()).toList())
        .doesNotHaveDuplicates();
    assertThat(Arrays.stream(migrations).map(m -> m.getScript()).toList())
        .contains("V22__linked_payee_destination_hash.sql", "V23__loan_salary_slips.sql")
        .doesNotContain("V22__loan_salary_slips.sql");
  }
}
