import java.sql.*;

/** Read-only schema inventory; connection comes exclusively from the environment. */
public class InspectDatabase {
  public static void main(String[] args) throws Exception {
    DriverManager.setLoginTimeout(10);
    try (var c =
            DriverManager.getConnection(
                System.getenv("BANKING_DB_URL"),
                System.getenv("BANKING_DB_USERNAME"),
                System.getenv("BANKING_DB_PASSWORD"));
        var s = c.createStatement()) {
      for (String sql :
          new String[] {
            "SELECT owner,table_name FROM all_tables WHERE table_name IN"
                + " ('CUSTOMERS','ACCOUNTS','USERS','BANKING_PRODUCTS','flyway_schema_history')"
                + " ORDER BY owner,table_name",
            "SELECT owner,name,type,referenced_name FROM all_dependencies WHERE referenced_name IN"
                + " ('USERS','BANKING_PRODUCTS','BENEFICIARIES','TRANSFERS','ACCOUNTS') AND owner"
                + " NOT IN ('SYS','PUBLIC')"
          }) {
        try (var r = s.executeQuery(sql)) {
          while (r.next()) {
            for (int i = 1; i <= r.getMetaData().getColumnCount(); i++)
              System.out.print(r.getString(i) + " ");
            System.out.println();
          }
        }
      }
    }
  }
}
