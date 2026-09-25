package com.nexa.api.controller;

import com.nexa.api.service.CurrentUserProvider;
import com.nexa.api.exep.ResourceNotFoundException;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Numbers are fetched explicitly, never included in saved conversation snapshots. */
@RestController
@RequestMapping("/api/v1/sensitive-numbers")
public class SensitiveNumbersController {
  private final JdbcTemplate db;
  private final CurrentUserProvider user;
  public SensitiveNumbersController(JdbcTemplate db, CurrentUserProvider user) {
    this.db = db; this.user = user;
  }

  @GetMapping("/{kind}/{id}")
  public ResponseEntity<Map<String, String>> number(@PathVariable String kind, @PathVariable String id) {
    String sql = switch (kind) {
      case "accounts" -> "SELECT CASE WHEN a.account_type IN ('SAVINGS','CURRENT','CARD') THEN a.account_number ELSE a.number_masked END FROM accounts a JOIN customers c ON c.id=a.customer_id WHERE c.user_id=? AND CAST(a.id AS VARCHAR(40))=?";
      case "cards", "loans" -> "SELECT " + (kind.equals("cards") ? "a.account_number" : "a.number_masked") + " FROM accounts a JOIN customers c ON c.id=a.customer_id WHERE c.user_id=? AND a.product_id=? AND a.account_type='" + (kind.equals("cards") ? "CARD" : "LOAN") + "'";
      case "bills" -> "SELECT destination_masked FROM transactions WHERE user_id=? AND id=? AND record_kind='BILL'";
      case "beneficiaries" -> "SELECT a.account_number FROM transactions t JOIN accounts a ON a.id=t.destination_account_id WHERE t.user_id=? AND t.id=? AND t.record_kind='BENEFICIARY'";
      case "mandates" -> "SELECT a.account_number FROM transactions t JOIN accounts a ON a.id=t.source_account_id JOIN customers c ON c.id=a.customer_id WHERE t.user_id=? AND t.id=? AND t.record_kind='MANDATE' AND c.user_id=t.user_id";
      case "transfer-destination" -> "SELECT a.account_number FROM transactions t JOIN accounts a ON a.id=t.destination_account_id WHERE t.user_id=? AND t.id=? AND t.record_kind='TRANSFER_REVIEW'";
      default -> throw new ResourceNotFoundException("Number not found.");
    };
    var values = db.query(sql, (rs, row) -> rs.getString(1), user.userId(), id);
    if (values.isEmpty()) throw new ResourceNotFoundException("Number not found.");
    String value = values.get(0);
    // Imported products may contain only masked digits; internal account IDs are not card numbers.
    boolean available = value != null && value.matches("[0-9][0-9 -]{5,29}");
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(available
        ? Map.of("number", value)
        : Map.of("message", "The bank has not provided the full number."));
  }
}
