package com.nexa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexa.api.NexaApiApplication;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Real HTTP/authentication and transactions over additive V32; all balances are isolated H2 fixtures. */
@SpringBootTest(classes = NexaApiApplication.class, properties = {
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.default_schema=PUBLIC", "spring.jpa.open-in-view=false",
    "spring.jpa.show-sql=false", "logging.level.root=WARN", "spring.datasource.hikari.schema=PUBLIC",
    "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'", "spring.sql.init.mode=never",
    "spring.config.import=", "nexa.onboarding.enabled=false", "nexa.onboarding.documents.enabled=false",
    "nexa.onboarding.documents.root=", "nexa.onboarding.identity.key-id=", "nexa.onboarding.identity.encryption-key=",
    "nexa.payouts.account-key=", "nexa.payouts.mode=DISABLED", "nexa.payouts.client-id=", "nexa.payouts.client-secret=",
    "nexa.admin.bootstrap.email=", "nexa.admin.bootstrap.password=", "nexa.ai.enabled=false",
    "nexa.security.jwt.secret=isolated-bank-funding-secret-with-more-than-32-bytes",
    "nexa.security.jwt.issuer=isolated-bank-funding", "nexa.cors.allowed-origins=http://localhost:8000"
})
@ActiveProfiles("integration")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthenticatedBankFundingIntegrationTest {
  private static final String API = "/api/v1/admin/bank-funding";
  private static final Connection KEEP_ALIVE = initialize();
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired com.nexa.api.security.JwtService jwt;
  private long admin, secondAdmin, customer, deposit;
  private int serial;

  @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> {
      try { return KEEP_ALIVE.getMetaData().getURL(); }
      catch (Exception error) { throw new IllegalStateException(error); }
    });
    properties.add("spring.datasource.username", () -> "sa");
    properties.add("spring.datasource.password", () -> "");
    properties.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
  }

  @BeforeEach void reset() {
    for (String table : List.of("bank_funding_receipts", "loan_salary_slips", "external_transfer_reviews",
        "external_bank_payees", "bill_payment_attempts", "application_events", "opening_cash_receipts",
        "application_documents", "account_applications", "ledger_entries", "journal_entries",
        "transactions", "customer_credentials")) db.update("DELETE FROM " + table);
    db.update("DELETE FROM accounts WHERE account_category='CUSTOMER' AND account_type IN ('LOAN','CARD')");
    db.update("DELETE FROM accounts WHERE account_category='CUSTOMER'");
    db.update("DELETE FROM customers");
    db.update("UPDATE accounts SET balance=0,status='ACTIVE',currency_code='INR',version=0 WHERE account_category='SYSTEM'");
    serial = 0;
    admin = person("ADMIN"); secondAdmin = person("ADMIN"); customer = person("CUSTOMER");
    db.update("INSERT INTO accounts(account_number,customer_id,account_name,account_type,account_category,"
        + "currency_code,balance,status,created_at,updated_at,version)"
        + " VALUES('910000002211',?,'Synthetic borrower savings','SAVINGS','CUSTOMER','INR',0,'ACTIVE',"
        + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", customer);
    deposit = db.queryForObject("SELECT id FROM accounts WHERE account_number='910000002211'", Long.class);
  }
  @AfterAll static void closeKeeper() throws Exception { KEEP_ALIVE.close(); }

  @Test void freshReserveIsZeroAndReadingDoesNotCreateFunding() throws Exception {
    var view = getJson(API, admin, "ADMIN", 200);
    assertThat(view.path("ready").asBoolean()).isTrue();
    assertThat(view.path("currencyCode").asText()).isEqualTo("INR");
    money(view, "reserveBalance", "0"); money(view, "cashBalance", "0");
    assertThat(view.path("receipts").size()).isZero();
    getJson(API + "/receipts/by-request/" + UUID.randomUUID(), admin, "ADMIN", 404);
    noPosting();
  }

  @Test void accessRequiresAuthenticatedCurrentlyActiveAdministrator() throws Exception {
    mvc.perform(get(API)).andExpect(status().isUnauthorized());
    getJson(API, customer, "CUSTOMER", 403);
    postJson(API + "/receipts", customer, "CUSTOMER", receipt("100.00"), 403);
    db.update("UPDATE customers SET role='CUSTOMER' WHERE id=?", admin);
    getJson(API, admin, "ADMIN", 403);
    postJson(API + "/receipts", admin, "ADMIN", receipt("100.00"), 403);
    db.update("UPDATE customers SET role='ADMIN',status='LOCKED' WHERE id=?", admin);
    // CurrentUserProvider rejects an inactive login with 401 before the service's role check.
    getJson(API, admin, "ADMIN", 401);
    postJson(API + "/receipts", admin, "ADMIN", receipt("100.00"), 401);
    noPosting();
  }

  @Test void confirmedCashReceiptPostsExactBalancedCapitalAndAuditWithoutCustomerCredit() throws Exception {
    var body = receipt("7000.25");
    body.put("source", "  Bank   capital contributor  ");
    body.put("reference", "  cash / sep - 001  ");
    body.put("reason", "  Original cash   receipt checked  ");
    var result = postJson(API + "/receipts", admin, "ADMIN", body, 200);
    assertThat(result.path("id").asText()).isEqualTo("BF-" + body.get("requestId"));
    assertThat(result.path("requestId").asText()).isEqualTo(body.get("requestId"));
    assertThat(result.path("receiptNumber").asText()).startsWith("BC-");
    assertThat(result.path("source").asText()).isEqualTo("Bank capital contributor");
    assertThat(result.path("reference").asText()).isEqualTo("CASH/SEP-001");
    assertThat(result.path("reason").asText()).isEqualTo("Original cash receipt checked");
    assertThat(result.path("recordedBy").asText()).isEqualTo("Synthetic Person 1");
    assertThat(Instant.parse(result.path("recordedAt").asText())).isNotNull();
    money(result, "amount", "7000.25"); money(result, "reserveBalanceAfter", "7000.25");
    money(result, "cashBalanceAfter", "7000.25");
    assertThat(balance("NEXA-BANK-FUNDING")).isEqualByComparingTo("7000.25");
    assertThat(balance("SYSTEM-CASH")).isEqualByComparingTo("7000.25");
    assertThat(balance("NEXA-LOAN-CONTROL")).isZero();
    assertThat(customerBalance()).isZero();
    assertThat(count("bank_funding_receipts")).isEqualTo(1);
    assertThat(count("transactions WHERE record_kind='PAYMENT'")).isEqualTo(1);
    assertThat(count("journal_entries WHERE status='POSTED'")).isEqualTo(1);
    assertThat(count("ledger_entries")).isEqualTo(2);
    String tx = result.path("transactionId").asText();
    assertThat(tx).isNotBlank();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM transactions WHERE record_kind='ADMIN_EVENT'"
        + " AND operation='BANK_CAPITAL_RECEIPT' AND user_id=? AND transaction_reference=?",
        Integer.class, userId(admin), tx)).isEqualTo(1);
    assertThat(db.queryForObject("SELECT l.amount FROM ledger_entries l JOIN accounts a ON a.id=l.account_id"
        + " WHERE a.account_number='SYSTEM-CASH' AND l.entry_type='DEBIT'", BigDecimal.class)).isEqualByComparingTo("7000.25");
    assertThat(db.queryForObject("SELECT l.amount FROM ledger_entries l JOIN accounts a ON a.id=l.account_id"
        + " WHERE a.account_number='NEXA-BANK-FUNDING' AND l.entry_type='CREDIT'", BigDecimal.class)).isEqualByComparingTo("7000.25");
    balanced();
    assertThat(getJson(API + "/receipts/by-request/" + body.get("requestId"), secondAdmin, "ADMIN", 200).path("id").asText())
        .isEqualTo(result.path("id").asText());
    assertThat(getJson(API, admin, "ADMIN", 200).path("receipts").size()).isEqualTo(1);
  }

  @Test void sameNormalizedPayloadReplaysAcrossAdminsAndChangedPayloadOrReferenceCannotPostAgain() throws Exception {
    var body = receipt("100.00"); body.put("reference", "cash / case - 01");
    var first = postJson(API + "/receipts", admin, "ADMIN", body, 200);
    var retry = new HashMap<>(body); retry.put("amount", "100.0"); retry.put("source", "  Bank   contributor  ");
    retry.put("reference", "CASH/CASE-01"); retry.put("reason", " Cash   counted and received ");
    assertThat(postJson(API + "/receipts", secondAdmin, "ADMIN", retry, 200).path("id").asText())
        .isEqualTo(first.path("id").asText());
    for (var change : Map.<String, Object>of("amount", "101", "source", "Another contributor", "reason", "Changed reason", "reference", "DIFFERENT").entrySet()) {
      var changed = new HashMap<>(body); changed.put(change.getKey(), change.getValue());
      postJson(API + "/receipts", admin, "ADMIN", changed, 409);
    }
    var duplicateReference = new HashMap<>(body); duplicateReference.put("requestId", UUID.randomUUID().toString());
    duplicateReference.put("reference", "  CASH/CASE-01  ");
    postJson(API + "/receipts", secondAdmin, "ADMIN", duplicateReference, 409);
    assertThat(count("bank_funding_receipts")).isEqualTo(1);
    assertThat(count("transactions WHERE record_kind='PAYMENT'")).isEqualTo(1);
    assertThat(balance("NEXA-BANK-FUNDING")).isEqualByComparingTo("100");
    assertThat(balance("SYSTEM-CASH")).isEqualByComparingTo("100");
    balanced();
  }

  @Test void invalidAmountsMissingConfirmationAndInvalidEvidenceAreRejectedBeforePosting() throws Exception {
    for (String amount : List.of("0", "-1", "0.001", "10000000.01", "1E+2147483647", "1E-2147483647"))
      postJson(API + "/receipts", admin, "ADMIN", receipt(amount), 400);
    for (String field : List.of("requestId", "amount", "source", "reference", "reason", "confirmed")) {
      var missing = receipt("1"); missing.remove(field);
      postJson(API + "/receipts", admin, "ADMIN", missing, 400);
    }
    for (var invalid : List.<Map.Entry<String, Object>>of(
        Map.entry("requestId", "not-a-uuid"), Map.entry("confirmed", false),
        Map.entry("source", " "), Map.entry("source", "a".repeat(161)), Map.entry("source", "漢".repeat(54)),
        Map.entry("reason", " "), Map.entry("reason", "a".repeat(501)), Map.entry("reason", "漢".repeat(167)),
        Map.entry("reference", " "), Map.entry("reference", "REF#1"), Map.entry("reference", "réf1"), Map.entry("reference", "ß"),
        Map.entry("reference", "A".repeat(81)))) {
      var body = receipt("1"); body.put(invalid.getKey(), invalid.getValue());
      postJson(API + "/receipts", admin, "ADMIN", body, 400);
    }
    noPosting();
  }

  @Test void unavailablePostingAccountsAreReportedAndCannotReceiveCapital() throws Exception {
    for (String number : List.of("NEXA-BANK-FUNDING", "SYSTEM-CASH")) {
      db.update("UPDATE accounts SET status='BLOCKED' WHERE account_number=?", number);
      assertThat(getJson(API, admin, "ADMIN", 200).path("ready").asBoolean()).isFalse();
      postJson(API + "/receipts", admin, "ADMIN", receipt("1"), 400);
      db.update("UPDATE accounts SET status='ACTIVE',currency_code='USD' WHERE account_number=?", number);
      assertThat(getJson(API, admin, "ADMIN", 200).path("ready").asBoolean()).isFalse();
      postJson(API + "/receipts", admin, "ADMIN", receipt("1"), 400);
      db.update("UPDATE accounts SET currency_code='INR' WHERE account_number=?", number);
    }
    noPosting();
  }

  @Test void negativeHistoricalReserveCanReceiveCashWithoutPretendingTheDeficitHasDisappeared() throws Exception {
    db.update("UPDATE accounts SET balance=-500 WHERE account_number='NEXA-BANK-FUNDING'");
    var result = postJson(API + "/receipts", admin, "ADMIN", receipt("300"), 200);
    money(result, "reserveBalanceAfter", "-200"); money(result, "cashBalanceAfter", "300");
    assertThat(balance("NEXA-BANK-FUNDING")).isEqualByComparingTo("-200");
    assertThat(customerBalance()).isZero(); balanced();
  }

  @Test void negativeCashAndBalanceOverflowAreRefusedWithoutPartialWrites() throws Exception {
    db.update("UPDATE accounts SET balance=-0.01 WHERE account_number='SYSTEM-CASH'");
    postJson(API + "/receipts", admin, "ADMIN", receipt("1"), 400);
    assertThat(count("bank_funding_receipts")).isZero();
    db.update("UPDATE accounts SET balance=0 WHERE account_number='SYSTEM-CASH'");
    for (String number : List.of("SYSTEM-CASH", "NEXA-BANK-FUNDING")) {
      db.update("UPDATE accounts SET balance=99999999999999999.99 WHERE account_number=?", number);
      postJson(API + "/receipts", admin, "ADMIN", receipt("0.01"), 400);
      assertThat(balance(number)).isEqualByComparingTo("99999999999999999.99");
      assertThat(count("transactions WHERE record_kind='PAYMENT'")).isZero();
      db.update("UPDATE accounts SET balance=0 WHERE account_number=?", number);
    }
    noPosting();
  }

  @Test void lateAuditFailureRollsBackReceiptPostingBalancesAndAllowsSameRequestRetry() throws Exception {
    var body = receipt("250.75");
    db.execute("ALTER TABLE transactions ADD CONSTRAINT h2_reject_capital_audit"
        + " CHECK (record_kind<>'ADMIN_EVENT' OR operation<>'BANK_CAPITAL_RECEIPT')");
    try {
      // The existing API handler maps persistence failures to 503; all writes must still roll back.
      assertThat(postJson(API + "/receipts", admin, "ADMIN", body, 503).path("code").asText())
          .isEqualTo("BANKING_UNAVAILABLE");
      noPosting();
      getJson(API + "/receipts/by-request/" + body.get("requestId"), admin, "ADMIN", 404);
    } finally { db.execute("ALTER TABLE transactions DROP CONSTRAINT h2_reject_capital_audit"); }
    postJson(API + "/receipts", admin, "ADMIN", body, 200);
    assertThat(count("bank_funding_receipts")).isEqualTo(1);
    assertThat(balance("NEXA-BANK-FUNDING")).isEqualByComparingTo("250.75"); balanced();
  }

  @Test void concurrentSameRequestAcrossAdminsPostsOnceAndBothReceiveTheSameReceipt() throws Exception {
    var body = receipt("125.25");
    List<JsonNode> results = together(List.of(
        () -> postJson(API + "/receipts", admin, "ADMIN", body, 200),
        () -> postJson(API + "/receipts", secondAdmin, "ADMIN", body, 200)));
    assertThat(results.get(0).path("id").asText()).isEqualTo(results.get(1).path("id").asText());
    assertThat(count("bank_funding_receipts")).isEqualTo(1);
    assertThat(count("transactions WHERE record_kind='PAYMENT'")).isEqualTo(1);
    assertThat(balance("NEXA-BANK-FUNDING")).isEqualByComparingTo("125.25"); balanced();
  }

  @Test void concurrentDifferentRequestsForOneReferenceCannotDoubleCountTheCash() throws Exception {
    var first = receipt("200"); var second = receipt("200");
    first.put("reference", "Concurrent / 001"); second.put("reference", "CONCURRENT/001");
    List<Integer> outcomes = together(List.of(
        () -> responseStatus(admin, first), () -> responseStatus(secondAdmin, second)));
    assertThat(outcomes).containsExactlyInAnyOrder(200, 409);
    assertThat(count("bank_funding_receipts")).isEqualTo(1);
    assertThat(count("transactions WHERE record_kind='PAYMENT'")).isEqualTo(1);
    assertThat(balance("NEXA-BANK-FUNDING")).isEqualByComparingTo("200"); balanced();
  }

  @Test void approvedSevenThousandLoanFailsUnfundedThenDisbursesExactlyOnceAfterReceipt() throws Exception {
    var application = Map.of("accountId", deposit, "applicationKey", "funding-workflow-loan",
        "purpose", "Education", "amount", "7000", "tenureMonths", 6);
    var upload = multipart("/api/v1/loans").file(new MockMultipartFile("application", "", "application/json",
        json.writeValueAsBytes(application)));
    for (var month : getJson("/api/v1/loans/salary-slip-requirements", customer, "CUSTOMER", 200)) {
      String period = month.asText();
      upload.param("months", period).file(new MockMultipartFile("files", period + ".pdf", "application/pdf",
          ("%PDF-1.4\n% Synthetic salary " + period + "\n%%EOF").getBytes(StandardCharsets.UTF_8)));
    }
    var created = json.readTree(mvc.perform(upload.header("Authorization", authorization(customer, "CUSTOMER")))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    String loanId = created.path("id").asText();
    assertThat(created.path("status").asText()).isEqualTo("PENDING_APPROVAL");
    var slips = db.queryForList("SELECT d.id FROM loan_salary_slips d JOIN accounts a ON a.id=d.loan_account_id"
        + " WHERE a.product_id=? ORDER BY d.salary_month", String.class, loanId);
    postJson("/api/v1/admin/loans/" + loanId + "/approve", admin, "ADMIN",
        Map.of("reason", "Synthetic salary slips reviewed", "verifiedSalarySlipIds", slips), 200);
    var failed = postJson("/api/v1/loans/" + loanId + "/disburse", customer, "CUSTOMER", Map.of(), 400);
    assertThat(failed.toString()).contains("bank funding account has insufficient funds");
    assertThat(customerBalance()).isZero(); assertThat(balance("NEXA-BANK-FUNDING")).isZero();
    assertThat(count("journal_entries")).isZero();
    assertThat(db.queryForObject("SELECT product_status FROM accounts WHERE product_id=?", String.class, loanId)).isEqualTo("APPROVED");
    var originalFunding = receipt("7000");
    var originalReceipt = postJson(API + "/receipts", admin, "ADMIN", originalFunding, 200);
    assertThat(customerBalance()).isZero();
    // Receipt and loan paths share sorted bank-account locks: neither may lose the other's update.
    List<JsonNode> concurrent = together(List.of(
        () -> postJson(API + "/receipts", secondAdmin, "ADMIN", receipt("1000"), 200),
        () -> postJson("/api/v1/loans/" + loanId + "/disburse", customer, "CUSTOMER", Map.of(), 200)));
    var paid = concurrent.get(1);
    var replay = postJson("/api/v1/loans/" + loanId + "/disburse", customer, "CUSTOMER", Map.of(), 200);
    assertThat(replay.path("transactionId").asText()).isEqualTo(paid.path("transactionId").asText());
    assertThat(customerBalance()).isEqualByComparingTo("7000");
    assertThat(balance("NEXA-BANK-FUNDING")).isEqualByComparingTo("1000");
    assertThat(balance("NEXA-LOAN-CONTROL")).isEqualByComparingTo("7000");
    assertThat(balance("SYSTEM-CASH")).isEqualByComparingTo("8000");
    var originalReplay = postJson(API + "/receipts", secondAdmin, "ADMIN", originalFunding, 200);
    assertThat(originalReplay.path("id").asText()).isEqualTo(originalReceipt.path("id").asText());
    money(originalReplay, "reserveBalanceAfter", "7000"); money(originalReplay, "cashBalanceAfter", "7000");
    assertThat(count("bank_funding_receipts")).isEqualTo(2);
    assertThat(count("transactions WHERE operation='LOAN_DISBURSEMENT' AND record_kind='PAYMENT'")).isEqualTo(1);
    assertThat(count("transactions WHERE record_kind='LOAN_INSTALLMENT'")).isEqualTo(6);
    assertThat(count("journal_entries WHERE status='POSTED'")).isEqualTo(3);
    assertThat(getJson("/api/v1/loans/" + loanId, customer, "CUSTOMER", 200).path("status").asText()).isEqualTo("ACTIVE");
    balanced();
  }

  private Map<String, Object> receipt(String amount) {
    return new HashMap<>(Map.of("requestId", UUID.randomUUID().toString(), "amount", amount,
        "source", "Bank contributor", "reference", "CASH-" + UUID.randomUUID(),
        "reason", "Cash counted and received", "confirmed", true));
  }
  private JsonNode postJson(String path, long who, String role, Object body, int expected) throws Exception {
    return json.readTree(mvc.perform(post(path).header("Authorization", authorization(who, role))
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
  }
  private JsonNode getJson(String path, long who, String role, int expected) throws Exception {
    return json.readTree(mvc.perform(get(path).header("Authorization", authorization(who, role)))
        .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
  }
  private int responseStatus(long who, Object body) throws Exception {
    return mvc.perform(post(API + "/receipts").header("Authorization", authorization(who, "ADMIN"))
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andReturn().getResponse().getStatus();
  }
  private <T> List<T> together(List<Callable<T>> work) throws Exception {
    var pool = Executors.newFixedThreadPool(work.size());
    try {
      var start = new CountDownLatch(1);
      var futures = new ArrayList<java.util.concurrent.Future<T>>();
      for (var task : work) futures.add(pool.submit(() -> { start.await(); return task.call(); }));
      start.countDown();
      var result = new ArrayList<T>();
      for (var future : futures) result.add(future.get(30, TimeUnit.SECONDS));
      return result;
    } finally { pool.shutdownNow(); }
  }
  private long person(String role) {
    String user = "funding-user-" + (++serial);
    db.update("INSERT INTO customers(user_id,full_name,email,status,role,created_at,updated_at)"
        + " VALUES(?,?,?,'ACTIVE',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        user, "Synthetic Person " + serial, user + "@example.test", role);
    db.update("INSERT INTO customer_credentials(id,user_id,credential_type,password_hash) VALUES(?,?,'PASSWORD','synthetic-fixture-hash')", user, user);
    return db.queryForObject("SELECT id FROM customers WHERE user_id=?", Long.class, user);
  }
  private String authorization(long id, String role) { return "Bearer " + jwt.issue(userId(id), "fixture@example.test", role).value(); }
  private String userId(long id) { return db.queryForObject("SELECT user_id FROM customers WHERE id=?", String.class, id); }
  private int count(String scope) { return db.queryForObject("SELECT COUNT(*) FROM " + scope, Integer.class); }
  private BigDecimal balance(String number) { return db.queryForObject("SELECT balance FROM accounts WHERE account_number=?", BigDecimal.class, number); }
  private BigDecimal customerBalance() { return db.queryForObject("SELECT balance FROM accounts WHERE id=?", BigDecimal.class, deposit); }
  private void money(JsonNode node, String field, String expected) { assertThat(new BigDecimal(node.path(field).asText())).isEqualByComparingTo(expected); }
  private void balanced() {
    assertThat(db.queryForObject("SELECT COUNT(*) FROM (SELECT journal_entry_id FROM ledger_entries"
        + " GROUP BY journal_entry_id HAVING SUM(CASE WHEN entry_type='DEBIT' THEN amount ELSE -amount END)<>0)", Integer.class)).isZero();
  }
  private void noPosting() {
    assertThat(count("bank_funding_receipts")).isZero();
    assertThat(count("transactions WHERE record_kind='PAYMENT'")).isZero();
    assertThat(count("transactions WHERE operation='BANK_CAPITAL_RECEIPT'")).isZero();
    assertThat(count("ledger_entries")).isZero(); assertThat(count("journal_entries")).isZero();
    assertThat(balance("SYSTEM-CASH")).isZero(); assertThat(balance("NEXA-BANK-FUNDING")).isZero();
    assertThat(balance("NEXA-LOAN-CONTROL")).isZero(); assertThat(customerBalance()).isZero();
  }
  private static Connection initialize() {
    try {
      var connection = AccountApplicationTestDatabase.initializedExternalTransferDatabase();
      ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V31__local_card_applications.sql"));
      ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V32__bank_funding_receipts.sql"));
      return connection;
    } catch (Exception error) { throw new ExceptionInInitializerError(error); }
  }
}
