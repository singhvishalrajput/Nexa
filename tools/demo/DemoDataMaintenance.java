import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/**
 * Offline, transactional removal of explicitly identified validation fixtures. Never a Flyway
 * migration.
 */
public class DemoDataMaintenance {
  static final List<String> TABLES =
      List.of(
          "CUSTOMERS",
          "CUSTOMER_CREDENTIALS",
          "ACCOUNTS",
          "TRANSACTIONS",
          "JOURNAL_ENTRIES",
          "LEDGER_ENTRIES",
          "CONVERSATIONS",
          "CONVERSATION_TURNS",
          "CONVERSATION_WORKFLOWS",
          "CONVERSATION_ACTION_EVENTS");
  static Connection db;

  static List<Map<String, Object>> rows(String sql) throws Exception {
    var result = new ArrayList<Map<String, Object>>();
    try (var s = db.createStatement();
        var r = s.executeQuery(sql)) {
      while (r.next()) {
        var row = new LinkedHashMap<String, Object>();
        for (int i = 1; i <= r.getMetaData().getColumnCount(); i++)
          row.put(r.getMetaData().getColumnName(i), r.getObject(i));
        result.add(row);
      }
    }
    return result;
  }

  static int execute(String sql, Object... args) throws Exception {
    try (var s = db.prepareStatement(sql)) {
      for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
      return s.executeUpdate();
    }
  }

  static String str(Object value) {
    return value == null ? null : value.toString();
  }

  static String text(String value) {
    return "'" + value.replace("'", "''") + "'";
  }

  static String literal(ResultSet r, int i) throws Exception {
    if (r.getObject(i) == null) return "NULL";
    String type = r.getMetaData().getColumnTypeName(i);
    if (type.equals("NUMBER") || type.equals("FLOAT")) return r.getBigDecimal(i).toPlainString();
    if (type.contains("WITH TIME ZONE"))
      return "TO_TIMESTAMP_TZ("
          + text(
              r.getObject(i, OffsetDateTime.class)
                  .format(
                      java.time.format.DateTimeFormatter.ofPattern(
                          "uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSSxxx")))
          + ",'YYYY-MM-DD\"T\"HH24:MI:SS.FFTZH:TZM')";
    if (type.startsWith("TIMESTAMP") || type.equals("DATE"))
      return "TIMESTAMP " + text(r.getTimestamp(i).toString());
    if (type.equals("RAW") || type.equals("BLOB"))
      return "HEXTORAW(" + text(HexFormat.of().formatHex(r.getBytes(i))) + ")";
    String value = r.getString(i);
    if (type.contains("CLOB") || value.length() > 1800) {
      var pieces = new ArrayList<String>();
      for (int n = 0; n < value.length(); n += 1800)
        pieces.add("TO_CLOB(" + text(value.substring(n, Math.min(n + 1800, value.length()))) + ")");
      return pieces.isEmpty() ? "EMPTY_CLOB()" : String.join("||", pieces);
    }
    return text(value);
  }

  static Path backup() throws Exception {
    Path dir = Path.of(".tools", "demo-backups", LocalDateTime.now().toString().replace(':', '-'));
    Files.createDirectories(dir);
    var deferred = new ArrayList<String>();
    try (var out = Files.newBufferedWriter(dir.resolve("restore.sql"))) {
      out.write(
          "-- Full pre-cleanup snapshot. Restore only offline to this schema; see"
              + " tools/demo/README.md.\n"
              + "SET DEFINE OFF;\n");
      out.write(
          "UPDATE transactions SET parent_id=NULL;\n"
              + "UPDATE accounts SET funding_account_id=NULL;\n");
      var reverse = new ArrayList<>(TABLES);
      Collections.reverse(reverse);
      for (String t : reverse) out.write("DELETE FROM " + t + ";\n");
      for (String t : TABLES) {
        int count = 0;
        try (var s = db.createStatement();
            var r =
                s.executeQuery(
                    "SELECT * FROM "
                        + t
                        + (t.equals("CONVERSATION_TURNS") ? " ORDER BY sequence_id" : ""))) {
          while (r.next()) {
            var columns = new ArrayList<String>();
            var values = new ArrayList<String>();
            for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) {
              String col = r.getMetaData().getColumnName(i), value = literal(r, i);
              // Oracle ALWAYS identity is a pagination sequence, not the message ID.
              // Preserve message IDs and ordering while Oracle regenerates this sequence on
              // restore.
              if (t.equals("CONVERSATION_TURNS") && col.equals("SEQUENCE_ID")) continue;
              columns.add(col);
              if ((t.equals("ACCOUNTS") && col.equals("FUNDING_ACCOUNT_ID")
                      || t.equals("TRANSACTIONS") && col.equals("PARENT_ID"))
                  && !value.equals("NULL")) {
                deferred.add(
                    "UPDATE "
                        + t
                        + " SET "
                        + col
                        + "="
                        + value
                        + " WHERE id="
                        + text(r.getString("ID"))
                        + ";\n");
                value = "NULL";
              }
              values.add(value);
            }
            out.write(
                "INSERT INTO "
                    + t
                    + "("
                    + String.join(",", columns)
                    + ") VALUES("
                    + String.join(",", values)
                    + ");\n");
            count++;
          }
        }
        System.out.println("Backed up " + t + ": " + count);
      }
      for (String sql : deferred) out.write(sql);
      out.write("COMMIT;\n");
    }
    byte[] digest =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(dir.resolve("restore.sql")));
    Files.writeString(
        dir.resolve("SHA256.txt"), HexFormat.of().formatHex(digest) + "  restore.sql\n");
    System.out.println("Snapshot: " + dir.toAbsolutePath());
    return dir;
  }

  static boolean fixture(String email) {
    return email.matches("six-[0-9a-f-]{36}@example\\.com")
        || email.matches("admin-[0-9a-f-]{36}@example\\.com")
        || email.matches("live-(sender|recipient)-[0-9a-f-]{36}@example\\.com");
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 1
        || !Set.of("inventory", "cleanup", "fund", "verify-backup").contains(args[0])
        || (args[0].equals("verify-backup") && args.length != 2)
        || (!args[0].equals("verify-backup")
            && !args[0].equals("fund")
            && args.length != 1)
        || (args[0].equals("fund") && (args.length < 1 || args.length > 2)))
      throw new IllegalArgumentException(
          "Use inventory, cleanup, fund [target-INR], or verify-backup <directory>");
    var props = new Properties();
    try (var r = Files.newBufferedReader(Path.of("apps/api/application-local.properties"))) {
      props.load(r);
    }
    String url =
        System.getenv().getOrDefault("BANKING_DB_URL", props.getProperty("spring.datasource.url"));
    String username =
        System.getenv()
            .getOrDefault("BANKING_DB_USERNAME", props.getProperty("spring.datasource.username"));
    String password =
        System.getenv()
            .getOrDefault("BANKING_DB_PASSWORD", props.getProperty("spring.datasource.password"));
    String schema =
        System.getenv()
            .getOrDefault(
                "BANKING_DB_SCHEMA", props.getProperty("spring.datasource.hikari.schema"));
    if (schema == null || !schema.matches("[A-Z][A-Z0-9_]*"))
      throw new IllegalArgumentException("Explicit uppercase schema required");
    try (var connection = DriverManager.getConnection(url, username, password)) {
      db = connection;
      db.setAutoCommit(false);
      try {
        execute("ALTER SESSION SET CURRENT_SCHEMA=" + schema);
        if(args[0].equals("verify-backup")) {
          Path directory=Path.of(args[1]).toAbsolutePath().normalize();
          if(!directory.startsWith(Path.of(".tools/demo-backups").toAbsolutePath().normalize()))throw new IllegalArgumentException("Expected a local demo backup directory");
          for(String table:TABLES)execute("LOCK TABLE "+table+" IN EXCLUSIVE MODE NOWAIT");
          verifyRestore(directory);db.rollback();return;
        }
        if (args[0].equals("fund")) {
          BigDecimal target =
              args.length == 2 ? new BigDecimal(args[1]) : new BigDecimal("10000000.00");
          fundBank(target);
          db.commit();
          return;
        }
        if (args[0].equals("cleanup"))
          for (String table : TABLES) execute("LOCK TABLE " + table + " IN EXCLUSIVE MODE NOWAIT");
        var customers = rows("SELECT id,user_id,email,role FROM customers");
        var removedUsers = new HashSet<String>();
        var removedCustomers = new HashSet<String>();
        for (var c : customers) {
          String email = str(c.get("EMAIL"));
          if (fixture(email)) {
            removedUsers.add(str(c.get("USER_ID")));
            removedCustomers.add(str(c.get("ID")));
          } else if (!Set.of(
                  "vishal@example.com", "admin@nexa.local", "asha@example.com", "rahul@example.com")
              .contains(email))
            throw new IllegalStateException("Unclassified customer; cleanup stopped: " + email);
        }
        System.out.println("Validation identities to remove: " + removedUsers.size());
        if (args[0].equals("inventory")) {
          db.rollback();
          return;
        }
        Path snapshot = backup();
        var accounts = rows("SELECT id,customer_id,account_type FROM accounts");
        var removedAccounts = new HashSet<String>();
        for (var a : accounts)
          if (removedCustomers.contains(str(a.get("CUSTOMER_ID"))))
            removedAccounts.add(str(a.get("ID")));
        var transactions =
            rows(
                "SELECT id,user_id,source_account_id,destination_account_id,parent_id FROM"
                    + " transactions");
        var removedTransactions = new HashSet<String>();
        for (var t : transactions)
          if (removedUsers.contains(str(t.get("USER_ID")))
              || removedAccounts.contains(str(t.get("SOURCE_ACCOUNT_ID")))
              || removedAccounts.contains(str(t.get("DESTINATION_ACCOUNT_ID"))))
            removedTransactions.add(str(t.get("ID")));
        boolean more = true;
        while (more) {
          more = false;
          for (var t : transactions)
            if (removedTransactions.contains(str(t.get("PARENT_ID"))))
              more |= removedTransactions.add(str(t.get("ID")));
        }
        var journals = rows("SELECT id,transaction_id FROM journal_entries");
        var removedJournals = new HashSet<String>();
        for (var j : journals)
          if (removedTransactions.contains(str(j.get("TRANSACTION_ID"))))
            removedJournals.add(str(j.get("ID")));
        var typeByAccount = new HashMap<String, String>();
        for (var a : accounts) typeByAccount.put(str(a.get("ID")), str(a.get("ACCOUNT_TYPE")));
        // Remove only the deleted fixtures' effect from surviving account balances (especially
        // system
        // cash).
        for (var l :
            rows("SELECT journal_entry_id,account_id,entry_type,amount FROM ledger_entries")) {
          String id = str(l.get("ACCOUNT_ID"));
          if (removedJournals.contains(str(l.get("JOURNAL_ENTRY_ID")))
              && !removedAccounts.contains(id)) {
            boolean debitNormal = Set.of("CASH", "LOAN", "CARD").contains(typeByAccount.get(id));
            BigDecimal delta = (BigDecimal) l.get("AMOUNT");
            if (debitNormal != str(l.get("ENTRY_TYPE")).equals("DEBIT")) delta = delta.negate();
            execute(
                "UPDATE accounts SET"
                    + " balance=balance-?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE"
                    + " id=?",
                delta,
                id);
          }
        }
        for (String id : removedJournals) {
          execute("DELETE FROM ledger_entries WHERE journal_entry_id=?", id);
          execute("DELETE FROM journal_entries WHERE id=?", id);
        }
        for (String id : removedTransactions)
          execute("UPDATE transactions SET parent_id=NULL WHERE id=?", id);
        for (String id : removedTransactions) execute("DELETE FROM transactions WHERE id=?", id);
        for (String id : removedAccounts)
          execute("UPDATE accounts SET funding_account_id=NULL WHERE id=?", id);
        for (String id : removedAccounts) execute("DELETE FROM accounts WHERE id=?", id);
        for (var c : rows("SELECT id,user_id FROM conversations"))
          if (removedUsers.contains(str(c.get("USER_ID")))) {
            for (String t : List.of("conversation_workflows", "conversation_turns"))
              execute("DELETE FROM " + t + " WHERE conversation_id=?", c.get("ID"));
            execute("DELETE FROM conversations WHERE id=?", c.get("ID"));
          }
        for (String id : removedUsers) {
          execute("DELETE FROM conversation_action_events WHERE user_id=?", id);
          execute("DELETE FROM customer_credentials WHERE user_id=?", id);
        }
        for (String id : removedCustomers) execute("DELETE FROM customers WHERE id=?", id);
        if (!rows("SELECT j.id FROM journal_entries j LEFT JOIN ledger_entries l ON"
                + " l.journal_entry_id=j.id GROUP BY j.id HAVING COUNT(l.id)<2 OR SUM(CASE WHEN"
                + " l.entry_type='DEBIT' THEN l.amount ELSE -l.amount END)<>0")
            .isEmpty())
          throw new IllegalStateException("Unbalanced remaining journal; cleanup rolled back");
        if (!rows("SELECT id FROM accounts WHERE account_category='CUSTOMER' AND balance<0")
            .isEmpty())
          throw new IllegalStateException("Negative customer balance; cleanup rolled back");
        String report =
            "Removed "
                + removedUsers.size()
                + " validation identities, "
                + removedAccounts.size()
                + " accounts, "
                + removedTransactions.size()
                + " transaction records. Kept demo/admin customers and unrelated chat data.\n";
        verifyRestore(snapshot);
        Files.writeString(snapshot.resolve("cleanup-report.txt"), report);
        db.commit();
        System.out.println(report);
      } catch (Exception failure) {
        db.rollback();
        throw failure;
      }
    }
  }

  static void verifyRestore(Path snapshot) throws Exception {
    // Prove the backup is executable before committing any deletions. Roll this rehearsal back.
    var savepoint = db.setSavepoint("CLEANED_DEMO");
    String sql =
        Files.readString(snapshot.resolve("restore.sql"))
            .replaceAll("(?m)^--[^\\r\\n]*[\\r\\n]+", "")
            .replace("SET DEFINE OFF;", "");
    var statement = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < sql.length(); i++) {
      char ch = sql.charAt(i);
      if (ch == '\'') {
        if (quoted && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
          statement.append("''");
          i++;
          continue;
        }
        quoted = !quoted;
      }
      if (ch == ';' && !quoted) {
        String command = statement.toString().trim();
        if (!command.isEmpty() && !command.equals("COMMIT")) execute(command);
        statement.setLength(0);
      } else statement.append(ch);
    }
    db.rollback(savepoint);
    Files.writeString(
        snapshot.resolve("restore-verified.txt"),
        "Full snapshot restored successfully inside a savepoint and rolled back before cleanup"
            + " commit.\n");
    System.out.println("Backup restore rehearsal passed");
  }

  static void fundBank(BigDecimal target) throws Exception {
    if (target.signum() <= 0
        || target.scale() > 2
        || target.compareTo(new BigDecimal("99999999999999999.99")) > 0)
      throw new IllegalArgumentException(
          "Funding target must be a positive INR amount with up to 2 decimals.");
    target = target.setScale(2);
    String capitalId = "TX-DC-" + target.toPlainString().replace('.', 'p');
    String journalReference = "J-DC-" + target.toPlainString().replace('.', 'p');
    var accounts =
        rows(
            "SELECT id,account_number,balance FROM accounts WHERE"
                + " account_number='NEXA-BANK-FUNDING' OR (account_category='SYSTEM' AND"
                + " account_type='CASH') ORDER BY id FOR UPDATE");
    if (accounts.size() != 2)
      throw new IllegalStateException("Expected bank funding and system cash accounts");
    if (!rows("SELECT id FROM transactions WHERE id=" + text(capitalId)).isEmpty()) {
      System.out.println("Bank capital for this target was already posted");
      return;
    }
    var bank =
        accounts.stream()
            .filter(a -> a.get("ACCOUNT_NUMBER").equals("NEXA-BANK-FUNDING"))
            .findFirst()
            .orElseThrow();
    var cash =
        accounts.stream()
            .filter(a -> !a.get("ID").equals(bank.get("ID")))
            .findFirst()
            .orElseThrow();
    BigDecimal amount = target.subtract((BigDecimal) bank.get("BALANCE"));
    if (amount.signum() <= 0) return;
    execute(
        "INSERT INTO"
            + " transactions(id,record_kind,transaction_type,destination_account_id,amount,currency_code,status,operation,transaction_reference,created_at,completed_at)"
            + " VALUES(?,'PAYMENT','DEPOSIT',?,?,'INR','SUCCESS','DEMO_BANK_CAPITAL',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        capitalId,
        bank.get("ID"),
        amount,
        capitalId);
    execute(
        "INSERT INTO journal_entries(transaction_id,entry_reference,entry_type,status,created_at)"
            + " VALUES(?,?,'TRANSACTION','POSTED',CURRENT_TIMESTAMP)",
        capitalId,
        journalReference);
    execute(
        "INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at)"
            + " SELECT id,?,'DEBIT',?,CURRENT_TIMESTAMP FROM journal_entries WHERE"
            + " transaction_id=?",
        cash.get("ID"),
        amount,
        capitalId);
    execute(
        "INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at)"
            + " SELECT id,?,'CREDIT',?,CURRENT_TIMESTAMP FROM journal_entries WHERE"
            + " transaction_id=?",
        bank.get("ID"),
        amount,
        capitalId);
    for (var a : accounts)
      execute(
          "UPDATE accounts SET balance=balance+?,version=version+1,updated_at=CURRENT_TIMESTAMP"
              + " WHERE id=?",
          amount,
          a.get("ID"));
    System.out.println(
        "Bank reserve funded to INR " + target.toPlainString() + " with a balanced capital journal");
  }
}
