package db.migration;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.flywaydb.core.api.migration.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** Copy-only cutover. Original tables remain until archive and verification are complete. */
public class V17__migrate_banking_products extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    JdbcTemplate db =
        new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));
    ObjectMapper json = JsonMapper.builder().build();
    db.query(
        "SELECT id,user_id,kind,account_id,payload FROM banking_products",
        (org.springframework.jdbc.core.RowCallbackHandler)
            r ->
                importProduct(
                    db,
                    r.getString(2),
                    r.getString(3),
                    r.getLong(4),
                    json.readTree(r.getString(5))));
    db.update(
        "INSERT INTO"
            + " transactions(id,record_kind,user_id,display_name,beneficiary_type,recipient_name,destination_masked,destination_hash,routing_code,status,bank_name,created_at,updated_at)"
            + " SELECT"
            + " id,'BENEFICIARY',user_id,display_name,beneficiary_type,account_holder_name,destination_account_masked,destination_account_hash,routing_code,status,bank_name,SYS_EXTRACT_UTC(created_at),SYS_EXTRACT_UTC(updated_at)"
            + " FROM beneficiaries");
    db.update(
        "INSERT INTO"
            + " transactions(id,record_kind,user_id,source_account_id,target_id,transaction_reference,amount,currency_code,status,created_at,updated_at,completed_at,due_at)"
            + " SELECT"
            + " id,'LEGACY_TRANSFER',user_id,source_account_id,beneficiary_id,transfer_reference,amount,currency_code,status,SYS_EXTRACT_UTC(created_at),SYS_EXTRACT_UTC(updated_at),SYS_EXTRACT_UTC(completed_at),TO_CHAR(scheduled_for,'YYYY-MM-DD\"T\"HH24:MI:SS')"
            + " FROM transfers");
    db.update(
        "INSERT INTO"
            + " transactions(id,record_kind,user_id,source_account_id,destination_account_id,source_name,source_masked,recipient_name,destination_masked,amount,currency_code,status,transaction_reference,created_at,expires_at,completed_at)"
            + " SELECT"
            + " id,'TRANSFER_REVIEW',user_id,source_account_id,destination_account_id,source_name,source_masked,recipient_name,destination_masked,amount,currency_code,status,transaction_reference,created_at,expires_at,completed_at"
            + " FROM money_transfer_requests");
    db.update(
        "INSERT INTO"
            + " transactions(id,record_kind,user_id,operation,source_account_id,target_id,amount,currency_code,status,expires_at,completed_at)"
            + " SELECT"
            + " id,'SIMULATION',user_id,operation,TO_NUMBER(account_id),target_id,TO_NUMBER(amount),currency_code,status,expires_at,completed_at"
            + " FROM showcase_actions");
    for (String[] pair :
        new String[][] {
          {"beneficiaries", "BENEFICIARY"},
          {"transfers", "LEGACY_TRANSFER"},
          {"money_transfer_requests", "TRANSFER_REVIEW"},
          {"showcase_actions", "SIMULATION"}
        }) {
      long old = db.queryForObject("SELECT COUNT(*) FROM " + pair[0], Long.class);
      long copied =
          db.queryForObject(
              "SELECT COUNT(*) FROM transactions WHERE record_kind=?", Long.class, pair[1]);
      if (old != copied) throw new IllegalStateException("Migration count mismatch: " + pair[0]);
    }
    long missing =
        db.queryForObject(
            "SELECT COUNT(*) FROM banking_products p WHERE NOT EXISTS(SELECT 1 FROM transactions t"
                + " WHERE t.id=p.id AND t.record_kind=p.kind) AND NOT EXISTS(SELECT 1 FROM accounts"
                + " a WHERE a.product_id=p.id AND a.account_type=p.kind)",
            Long.class);
    if (missing != 0) throw new IllegalStateException("Product migration incomplete");
  }

  static String str(JsonNode p, String key) {
    var n = p.get(key);
    return n == null || n.isNull() ? null : n.asText();
  }

  static BigDecimal decimal(JsonNode p, String key) {
    String s = str(p, key);
    return s == null ? null : new BigDecimal(s);
  }

  public static void importProduct(
      JdbcTemplate db, String user, String kind, long funding, JsonNode p) {
    String id = str(p, "id");
    if (kind.equals("LOAN") || kind.equals("CARD")) {
      var customer =
          db.queryForObject(
              "SELECT customer_id FROM accounts a JOIN customers c ON c.id=a.customer_id WHERE"
                  + " a.id=? AND c.user_id=?",
              Long.class,
              funding,
              user);
      // Legacy projections do not contain original principal. Preserve it as unknown, never invent
      // a disbursement.
      db.update(
          "INSERT INTO"
              + " accounts(product_id,account_number,customer_id,account_name,account_type,account_category,currency_code,balance,status,created_at,updated_at,version,funding_account_id,interest_rate,product_status,product_type,number_masked,due_at,periodic_payment,credit_limit,minimum_payment)"
              + " VALUES(?,?,?,?,?,'CUSTOMER',?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,?,?,?,?,?,?,?,?,?)",
          id,
          "P-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26),
          customer,
          str(p, "displayName"),
          kind,
          str(p, "currencyCode"),
          decimal(p, "outstanding"),
          funding,
          decimal(p, "interestRate"),
          str(p, "status"),
          str(p, kind.equals("LOAN") ? "loanType" : "cardType"),
          str(p, "numberMasked"),
          str(p, "dueAt"),
          decimal(p, "nextEmi"),
          decimal(p, "creditLimit"),
          decimal(p, "minimumPayment"));
    } else {
      db.update(
          "INSERT INTO"
              + " transactions(id,record_kind,user_id,source_account_id,display_name,status,amount,minimum_amount,currency_code,frequency,effective_date,end_date,due_at,transaction_reference,category,destination_masked)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
          id,
          kind,
          user,
          funding,
          str(p, kind.equals("BILL") ? "billerName" : "payee"),
          str(p, "status"),
          decimal(p, kind.equals("MANDATE") ? "limit" : "amount"),
          decimal(p, "minimumAmount"),
          str(p, "currencyCode"),
          str(p, "frequency"),
          str(p, "startDate"),
          str(p, "endDate"),
          str(p, kind.equals("MANDATE") ? "nextDebit" : "dueAt"),
          str(p, "reference"),
          str(p, "category"),
          str(p, "customerNumberMasked"));
    }
    for (String history : List.of("paymentHistory", "transactions")) {
      var rows = p.get(history);
      if (rows == null || !rows.isArray()) continue;
      for (var h : rows) {
        String occurred = str(h, "occurredAt");
        Timestamp at =
            occurred == null
                ? Timestamp.from(Instant.now())
                : Timestamp.from(OffsetDateTime.parse(occurred).toInstant());
        db.update(
            "INSERT INTO"
                + " transactions(id,record_kind,user_id,source_account_id,target_id,display_name,amount,currency_code,due_at,status,transaction_reference,operation,merchant_name,category,payment_method,created_at)"
                + " VALUES(?,'PRODUCT_HISTORY',?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            str(h, "id"),
            user,
            funding,
            id,
            str(h, "payee"),
            decimal(h, "amount"),
            str(h, "currencyCode"),
            str(h, "dueAt"),
            str(h, "status"),
            str(h, "reference"),
            str(h, "type"),
            str(h, "merchantName"),
            str(h, "category"),
            str(h, "paymentMethod"),
            at);
      }
    }
  }
}
