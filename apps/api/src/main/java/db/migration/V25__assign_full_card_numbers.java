package db.migration;

import com.nexa.api.service.CardNumbers;
import org.flywaydb.core.api.migration.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

public class V25__assign_full_card_numbers extends BaseJavaMigration {
  @Override public void migrate(Context context) {
    backfill(new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true)));
  }
  public static void backfill(JdbcTemplate db) {
    var rows = db.queryForList("SELECT id,account_number,number_masked FROM accounts WHERE account_type='CARD'");
    for (var row : rows) {
      String old = (String) row.get("ACCOUNT_NUMBER");
      String full = old != null && old.matches("[0-9]{16}") ? old
          : CardNumbers.forAccount(((Number) row.get("ID")).longValue(), (String) row.get("NUMBER_MASKED"));
      db.update("UPDATE accounts SET account_number=?,number_masked=? WHERE id=?",
          full, "•••• " + full.substring(12), row.get("ID"));
    }
  }
}
