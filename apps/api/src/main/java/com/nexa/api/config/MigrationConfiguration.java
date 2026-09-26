package com.nexa.api.config;

import java.sql.SQLException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MigrationConfiguration {
  @Bean
  FlywayConfigurationCustomizer seedFreeMigrationLocations() {
    return configuration -> {
      try (var connection = configuration.getDataSource().getConnection()) {
        configuration.locations(
            MigrationLocations.select(connection, configuration.getDefaultSchema()));
      } catch (SQLException exception) {
        throw new IllegalStateException(
            "Could not inspect migration history; refusing to select demo migration resources.",
            exception);
      }
    };
  }
}
