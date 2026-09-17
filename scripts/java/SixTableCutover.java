import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.zip.*;
import org.flywaydb.core.Flyway;
import tools.jackson.databind.json.JsonMapper;

/**
 * Run with the application's runtime classpath plus target/classes; all secrets are
 * environment-only.
 */
public class SixTableCutover {
  static final List<String> OLD =
      List.of(
          "SHOWCASE_ACTIONS",
          "MONEY_TRANSFER_REQUESTS",
          "BANKING_PRODUCTS",
          "TRANSFER_APPROVALS",
          "BENEFICIARY_VERIFICATIONS",
          "TRANSFERS",
          "BENEFICIARIES",
          "IDEMPOTENCY_RECORDS",
          "AUDIT_EVENTS",
          "OUTBOX_EVENTS",
          "AUTH_REFRESH_TOKENS",
          "BANKING_ACCOUNT_MIGRATION",
          "LEGACY_LEDGER_POSTINGS",
          "LEGACY_TRANSACTIONS",
          "LEGACY_JOURNAL_ENTRIES",
          "LEGACY_LEDGER_ACCOUNTS",
          "LEGACY_BANK_ACCOUNTS",
          "USERS");
  static String schema = System.getenv().getOrDefault("BANKING_DB_SCHEMA", "NEXA_APP");
  static String url = System.getenv("BANKING_DB_URL"),
      user = System.getenv("BANKING_DB_USERNAME"),
      password = System.getenv("BANKING_DB_PASSWORD");

  static Connection connect() throws Exception {
    if (!schema.matches("NEXA_[A-Z0-9_]+"))
      throw new IllegalArgumentException("Expected an existing NEXA_ application schema");
    var c = DriverManager.getConnection(url, user, password);
    try (var s = c.createStatement()) {
      s.execute("ALTER SESSION SET CURRENT_SCHEMA=" + schema);
    }
    return c;
  }

  static void entry(ZipOutputStream zip, String name, String value) throws Exception {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(value.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  static String rows(Connection c, String table) throws Exception {
    var out = new ArrayList<Map<String, Object>>();
    try (var s = c.createStatement();
        var r = s.executeQuery("SELECT * FROM " + schema + ".\"" + table + "\"")) {
      while (r.next()) {
        var row = new LinkedHashMap<String, Object>();
        for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) {
          Object v = r.getObject(i);
          if (v instanceof Clob cl) v = cl.getSubString(1, Math.toIntExact(cl.length()));
          else if (v instanceof Blob b)
            v = Base64.getEncoder().encodeToString(b.getBytes(1, Math.toIntExact(b.length())));
          else if (v != null && !(v instanceof Number)) v = r.getString(i);
          row.put(r.getMetaData().getColumnName(i), v);
        }
        out.add(row);
      }
    }
    // Canonical order permits byte-for-byte comparison before retirement.
    var json = JsonMapper.builder().build();
    var sorted = out.stream().map(json::writeValueAsString).sorted().toList();
    return "[" + String.join(",", sorted) + "]";
  }

  static String ddl(Connection c, String table) throws Exception {
    try (var q = c.prepareStatement("SELECT DBMS_METADATA.GET_DDL('TABLE',?,?) FROM dual")) {
      q.setString(1, table);
      q.setString(2, schema);
      try (var r = q.executeQuery()) {
        r.next();
        return r.getString(1);
      }
    }
  }

  static long count(Connection c, String sql) throws Exception {
    try (var s = c.createStatement();
        var r = s.executeQuery(sql)) {
      r.next();
      return r.getLong(1);
    }
  }

  static void verify(Connection c) throws Exception {
    if (count(
            c,
            "SELECT COUNT(*) FROM (SELECT j.id FROM journal_entries j LEFT JOIN ledger_entries l ON"
                + " l.journal_entry_id=j.id GROUP BY j.id HAVING COUNT(l.id)<2 OR SUM(CASE WHEN"
                + " l.entry_type='DEBIT' THEN l.amount ELSE -l.amount END)<>0)")
        != 0) throw new IllegalStateException("Unbalanced or empty journal");
    if (count(
            c,
            "SELECT COUNT(*) FROM accounts a LEFT JOIN customers c ON c.id=a.customer_id WHERE"
                + " a.account_category='CUSTOMER' AND c.id IS NULL")
        != 0) throw new IllegalStateException("Orphan customer account");
    if (count(
                c,
                "SELECT COUNT(*) FROM all_tables WHERE owner='"
                    + schema
                    + "' AND table_name='USERS'")
            > 0
        && count(
                c,
                "SELECT COUNT(*) FROM users u WHERE NOT EXISTS(SELECT 1 FROM customers c JOIN"
                    + " customer_credentials k ON k.user_id=c.user_id AND"
                    + " k.credential_type='PASSWORD' WHERE c.user_id=u.id AND"
                    + " (k.password_hash=u.password_hash OR (k.password_hash IS NULL AND"
                    + " u.password_hash IS NULL)))")
            != 0) throw new IllegalStateException("Login migration incomplete");
    if (count(
            c,
            "SELECT COUNT(*) FROM transactions t WHERE t.record_kind='PAYMENT' AND t.id LIKE 'TX-%'"
                + " AND t.status='SUCCESS' AND NOT EXISTS(SELECT 1 FROM journal_entries j WHERE"
                + " j.transaction_id=t.id)")
        != 0) throw new IllegalStateException("Posted transaction lacks journal");
    System.out.println("Identity, ownership and journal verification passed.");
  }

  public static void main(String[] args) throws Exception {
    DriverManager.setLoginTimeout(10);
    switch (args[0]) {
      case "archive":
        try (var c = connect();
            var zip =
                new ZipOutputStream(
                    Files.newOutputStream(Path.of(args[1]), StandardOpenOption.CREATE_NEW))) {
          c.setAutoCommit(false);
          c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
          List<String> tables = new ArrayList<>();
          try (var q =
              c.prepareStatement(
                  "SELECT table_name FROM all_tables WHERE owner=? ORDER BY table_name")) {
            q.setString(1, schema);
            try (var r = q.executeQuery()) {
              while (r.next()) tables.add(r.getString(1));
            }
          }
          for (String t : tables) {
            entry(zip, t + ".json", rows(c, t));
            entry(zip, t + ".sql", ddl(c, t));
          }
          entry(zip, "schema.txt", schema);
          c.rollback();
          System.out.println("Archived " + tables.size() + " application tables to " + args[1]);
        }
        break;
      case "migrate":
        Flyway.configure()
            .dataSource(url, user, password)
            .defaultSchema(schema)
            .schemas(schema)
            .initSql("ALTER SESSION SET CURRENT_SCHEMA=" + schema)
            .locations("classpath:db/migration", "classpath:db/local-migration")
            .cleanDisabled(true)
            .load()
            .migrate();
        break;
      case "inventory":
        try (var c = connect();
            var q =
                c.prepareStatement(
                    "SELECT table_name FROM all_tables WHERE owner=? ORDER BY table_name")) {
          q.setString(1, schema);
          try (var r = q.executeQuery()) {
            while (r.next()) System.out.println(r.getString(1));
          }
        }
        break;
      case "verify":
        try (var c = connect()) {
          verify(c);
          if (args.length > 1) {
            try (var zip = new ZipFile(args[1])) {
              for (String table :
                  List.of(
                      "CONVERSATIONS",
                      "CONVERSATION_TURNS",
                      "CONVERSATION_WORKFLOWS",
                      "CONVERSATION_ACTION_EVENTS")) {
                String archived =
                    new String(
                        zip.getInputStream(zip.getEntry(table + ".json")).readAllBytes(),
                        StandardCharsets.UTF_8);
                if (!archived.equals(rows(c, table)))
                  throw new IllegalStateException("Chat data changed since archive: " + table);
              }
              System.out.println("All four chat tables match their archived rows exactly.");
            }
          }
        }
        break;
      case "schema":
        try (var c = connect()) {
          try (var st = c.createStatement()) {
            st.execute(
                "BEGIN"
                    + " DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM,'SEGMENT_ATTRIBUTES',FALSE);"
                    + " END;");
          }
          var sql =
              new StringBuilder(
                  "-- Final Oracle banking schema, extracted from the verified database.\n");
          for (String t :
              List.of(
                  "CUSTOMERS",
                  "CUSTOMER_CREDENTIALS",
                  "ACCOUNTS",
                  "TRANSACTIONS",
                  "JOURNAL_ENTRIES",
                  "LEDGER_ENTRIES")) sql.append(ddl(c, t)).append(";\n");
          try (var q =
              c.prepareStatement(
                  "SELECT i.index_name FROM all_indexes i WHERE i.owner=? AND i.table_name IN"
                      + " ('CUSTOMERS','CUSTOMER_CREDENTIALS','ACCOUNTS','TRANSACTIONS','JOURNAL_ENTRIES','LEDGER_ENTRIES')"
                      + " AND NOT EXISTS(SELECT 1 FROM all_constraints k WHERE k.owner=i.owner AND"
                      + " k.index_name=i.index_name) ORDER BY i.index_name")) {
            q.setString(1, schema);
            try (var r = q.executeQuery()) {
              while (r.next())
                try (var d =
                    c.prepareStatement("SELECT DBMS_METADATA.GET_DDL('INDEX',?,?) FROM dual")) {
                  d.setString(1, r.getString(1));
                  d.setString(2, schema);
                  try (var x = d.executeQuery()) {
                    x.next();
                    sql.append(x.getString(1)).append(";\n");
                  }
                }
            }
          }
          Files.writeString(Path.of(args[1]), sql.toString().replace("\"" + schema + "\".", ""));
        }
        break;
      case "retire":
        try (var c = connect();
            var zip = new ZipFile(args[1])) {
          if (!new String(
                  zip.getInputStream(zip.getEntry("schema.txt")).readAllBytes(),
                  StandardCharsets.UTF_8)
              .equals(schema)) throw new IllegalStateException("Wrong archive schema");
          verify(c);
          // Verify every obsolete table is archived exactly, before the first DDL auto-commit.
          for (String t : OLD) {
            var e = zip.getEntry(t + ".json");
            if (e == null
                || !new String(zip.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8)
                    .equals(rows(c, t)))
              throw new IllegalStateException("Missing or changed archive: " + t);
          }
          for (String t : OLD)
            try (var s = c.createStatement()) {
              s.execute("DROP TABLE " + schema + "." + t);
              System.out.println("Retired " + t);
            }
        }
        break;
      default:
        throw new IllegalArgumentException(
            "archive <zip> | migrate | verify | schema <sql> | retire <zip>");
    }
  }
}
