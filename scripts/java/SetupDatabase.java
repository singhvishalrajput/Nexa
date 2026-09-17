import java.nio.file.Path;
import java.sql.*;
import org.flywaydb.core.Flyway;

/** Source-launched by setup-db.ps1; intentionally outside the application build. */
public class SetupDatabase {
  static String identifier(String value) {
    if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,29}"))
      throw new IllegalArgumentException(
          "Use an uppercase Oracle identifier, at most 30 characters.");
    return value;
  }

  static String schema(String value) {
    identifier(value);
    if (!value.matches("NEXA_[A-Z0-9_]{1,25}"))
      throw new IllegalArgumentException("The application schema must start with NEXA_.");
    return value;
  }

  static void password(String value, boolean creating) {
    if (value == null
        || value.isEmpty()
        || (creating && value.length() < 12)
        || value.indexOf('"') >= 0
        || value.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException(
          "Use a nonempty password without double quotes or control characters; new schema"
              + " passwords need 12+ characters.");
  }

  static void requirePdb(String name) {
    if (name == null
        || name.isBlank()
        || name.equalsIgnoreCase("CDB$ROOT")
        || name.equalsIgnoreCase("PDB$SEED"))
      throw new IllegalArgumentException(
          "Connect to an open application PDB such as FREEPDB1, not the root or seed container.");
  }

  static void checkContainer(Connection connection) throws SQLException {
    try (var statement = connection.createStatement();
        var result =
            statement.executeQuery("SELECT SYS_CONTEXT('USERENV', 'CON_NAME') FROM dual")) {
      result.next();
      requirePdb(result.getString(1));
    }
  }

  static String required(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("Missing setup configuration: " + key);
    return value;
  }

  public static void main(String[] args) {
    try {
      String url = required("NEXA_SETUP_URL");
      String app = schema(required("NEXA_SETUP_SCHEMA"));
      String secret = required("NEXA_SETUP_PASSWORD");
      password(secret, false);
      DriverManager.setLoginTimeout(20);
      if (!Boolean.parseBoolean(System.getenv("NEXA_SETUP_EXISTING"))) {
        String admin = identifier(required("NEXA_SETUP_ADMIN"));
        if (admin.equals("SYS"))
          throw new IllegalArgumentException("Use SYSTEM or a PDB administrator, not SYS.");
        String tablespace = identifier(required("NEXA_SETUP_TABLESPACE"));
        try (var connection =
            DriverManager.getConnection(url, admin, required("NEXA_SETUP_ADMIN_PASSWORD"))) {
          checkContainer(connection);
          boolean exists;
          try (var query =
              connection.prepareStatement("SELECT COUNT(*) FROM dba_users WHERE username = ?")) {
            query.setString(1, app);
            try (var result = query.executeQuery()) {
              result.next();
              exists = result.getInt(1) != 0;
            }
          }
          if (exists) {
            System.out.println("Schema already exists; preserving its password, grants and data.");
          } else {
            password(secret, true);
            try (var statement = connection.createStatement()) {
              // Identifiers are allowlisted; Oracle quoted passwords cannot contain double quotes.
              statement.execute(
                  "CREATE USER "
                      + app
                      + " IDENTIFIED BY \""
                      + secret
                      + "\" DEFAULT TABLESPACE "
                      + tablespace
                      + " QUOTA 500M ON "
                      + tablespace);
              statement.execute("GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE TO " + app);
            }
            System.out.println("Created dedicated schema " + app + " with a 500 MB quota.");
          }
        }
      }
      // Always authenticate as the app owner before allowing migrations to touch its schema.
      try (var connection = DriverManager.getConnection(url, app, secret)) {
        checkContainer(connection);
      }
      Path resources = Path.of(required("NEXA_SETUP_RESOURCES")).toAbsolutePath();
      if (!java.nio.file.Files.isDirectory(resources.resolve("db/migration"))
          || !java.nio.file.Files.isDirectory(resources.resolve("db/local-migration")))
        throw new IllegalArgumentException(
            "Migration directories are missing. Pull the full repository before setup.");
      var flyway =
          Flyway.configure()
              .dataSource(url, app, secret)
              .defaultSchema(app)
              .schemas(app)
              .locations("classpath:db/migration", "classpath:db/local-migration")
              .cleanDisabled(true)
              .baselineOnMigrate(false)
              .validateOnMigrate(true)
              .load();
      var result = flyway.migrate();
      System.out.println(
          "Database ready. Applied "
              + result.migrationsExecuted
              + " migration(s). Existing migrations validated.");
    } catch (IllegalArgumentException ex) {
      System.err.println(ex.getMessage());
      System.exit(1);
    } catch (SQLException ex) {
      // Never print the SQL statement: CREATE USER contains the new password.
      System.err.println(
          "Oracle setup failed (ORA-"
              + String.format("%05d", ex.getErrorCode())
              + "). Check credentials, PDB/service, tablespace and administrator privileges. No"
              + " cleanup/drop was performed.");
      System.exit(1);
    } catch (org.flywaydb.core.api.FlywayException ex) {
      String detail = ex.getMessage();
      if (detail != null) {
        for (String key : new String[] {"NEXA_SETUP_PASSWORD", "NEXA_SETUP_ADMIN_PASSWORD"}) {
          String secret = System.getenv(key);
          if (secret != null && !secret.isEmpty()) detail = detail.replace(secret, "[redacted]");
        }
        System.err.println(detail);
      }
      System.err.println(
          "Migration failed. Oracle DDL can commit partially; inspect the migration history before"
              + " retrying. No automatic repair, baseline or clean was performed.");
      System.exit(1);
    }
  }
}
