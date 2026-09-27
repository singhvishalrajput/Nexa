package com.nexa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexa.api.NexaApiApplication;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Real Spring transactions/authentication, additive V31, and synthetic in-memory balances only. */
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
    "nexa.security.jwt.secret=isolated-card-applications-secret-with-more-than-32-bytes",
    "nexa.security.jwt.issuer=isolated-card-applications", "nexa.cors.allowed-origins=http://localhost:8000"
})
@ActiveProfiles("integration")
@AutoConfigureMockMvc
@Import(AuthenticatedCardApplicationsIntegrationTest.FixedTime.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthenticatedCardApplicationsIntegrationTest {
  private static final String CARDS = "/api/v1/cards";
  private static final String ADMIN = "/api/v1/admin/card-applications";
  private static final Instant NOW = Instant.parse("2026-09-26T20:00:00Z"); // September 27 in India.
  private static final Connection KEEP_ALIVE = initialize();
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired com.nexa.api.security.JwtService jwt;
  private long customer, other, admin, funding, destination;
  private int serial;

  @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> {
      try { return KEEP_ALIVE.getMetaData().getURL() + ";MODE=Oracle;LOCK_TIMEOUT=10000"; }
      catch (Exception error) { throw new IllegalStateException(error); }
    });
    properties.add("spring.datasource.username", () -> "sa");
    properties.add("spring.datasource.password", () -> "");
    properties.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
  }

  @BeforeEach void reset() {
    for (String table : List.of("external_transfer_reviews", "external_bank_payees", "bill_payment_attempts",
        "application_events", "opening_cash_receipts", "application_documents", "account_applications",
        "ledger_entries", "journal_entries", "transactions", "customer_credentials")) db.update("DELETE FROM " + table);
    // Product accounts refer to their funding deposits; preserve FK enforcement during cleanup.
    db.update("DELETE FROM accounts WHERE account_category='CUSTOMER' AND account_type IN ('CARD','LOAN')");
    db.update("DELETE FROM accounts WHERE account_category='CUSTOMER'");
    db.update("DELETE FROM customers");
    serial = 0;
    customer = customer("CUSTOMER"); other = customer("CUSTOMER"); admin = customer("ADMIN");
    funding = account(customer, "SAVINGS", "ACTIVE", "INR", "1000.00");
    destination = account(other, "CURRENT", "ACTIVE", "INR", "200.00");
  }
  @AfterAll static void closeKeeper() throws Exception { KEEP_ALIVE.close(); }

  @Test void debitApplicationCreatesOneLocalRecordWithoutMoneyMovementAndReplays() throws Exception {
    String key = UUID.randomUUID().toString();
    var payload = application(funding, "DEBIT", key);
    JsonNode first = postJson(CARDS + "/applications", customer, "CUSTOMER", payload, 200);
    assertThat(first.path("status").asText()).isEqualTo("ACTIVE");
    assertThat(first.path("cardType").asText()).isEqualTo("DEBIT");
    assertThat(first.path("numberMasked").asText().replaceAll("\\D", "")).hasSize(4);
    assertThat(new BigDecimal(first.path("creditLimit").asText())).isEqualByComparingTo("0");
    assertThat(postJson(CARDS + "/applications", customer, "CUSTOMER", payload, 200).path("id").asText()).isEqualTo(first.path("id").asText());
    postJson(CARDS + "/applications", customer, "CUSTOMER", application(funding, "DEBIT", UUID.randomUUID().toString()), 409);
    assertThat(count("accounts WHERE account_type='CARD'")).isEqualTo(1);
    assertThat(count("transactions WHERE operation='CARD_APPLICATION'")).isEqualTo(1);
    noMoneyMoved();
    assertThat(db.queryForObject("SELECT account_number FROM accounts WHERE account_type='CARD'", String.class)).startsWith("LOCAL-").hasSize(30);
  }

  @Test void creditNeedsActiveAdminApprovalWithBankSetLimitAndAuditedIdempotentDecision() throws Exception {
    var payload = new java.util.HashMap<String, Object>(application(funding, "CREDIT", UUID.randomUUID().toString()));
    payload.put("creditLimit", 999999);
    JsonNode card = postJson(CARDS + "/applications", customer, "CUSTOMER", payload, 200);
    String id = card.path("id").asText();
    assertThat(card.path("status").asText()).isEqualTo("PENDING_APPROVAL");
    assertThat(new BigDecimal(card.path("creditLimit").asText())).isEqualByComparingTo("0");
    assertThat(getJson(ADMIN + "?status=PENDING_APPROVAL", admin, "ADMIN").size()).isEqualTo(1);
    postJson(ADMIN + "/" + id + "/approve", customer, "CUSTOMER", Map.of("creditLimit", 2000), 403);
    postJson(CARDS + "/" + id + "/unblock", customer, "CUSTOMER", Map.of(), 409);
    var approval = Map.of("creditLimit", 2000, "reason", "Reviewed application");
    assertThat(postJson(ADMIN + "/" + id + "/approve", admin, "ADMIN", approval, 200).path("status").asText()).isEqualTo("ACTIVE");
    postJson(ADMIN + "/" + id + "/approve", admin, "ADMIN", approval, 200);
    postJson(ADMIN + "/" + id + "/approve", admin, "ADMIN", Map.of("creditLimit", 3000), 409);
    assertThat(new BigDecimal(getJson(CARDS + "/" + id, customer, "CUSTOMER").path("creditLimit").asText()))
        .isEqualByComparingTo("2000.00");
    assertThat(count("transactions WHERE operation='CARD_APPROVE'")).isEqualTo(1);
    assertThat(db.queryForObject("SELECT user_id FROM transactions WHERE operation='CARD_APPROVE'", String.class)).isEqualTo(userId(admin));
    noMoneyMoved();
  }

  @Test void rejectionNeedsReasonIsAuditedAndAllowsNewApplication() throws Exception {
    String id = apply("CREDIT");
    postJson(ADMIN + "/" + id + "/reject", admin, "ADMIN", Map.of(), 400);
    var decision = Map.of("reason", "Credit eligibility not met");
    postJson(ADMIN + "/" + id + "/reject", admin, "ADMIN", decision, 200);
    postJson(ADMIN + "/" + id + "/reject", admin, "ADMIN", decision, 200);
    postJson(ADMIN + "/" + id + "/approve", admin, "ADMIN", Map.of("creditLimit", 2000), 409);
    assertThat(getJson(CARDS + "/" + id, customer, "CUSTOMER").path("status").asText()).isEqualTo("REJECTED");
    assertThat(apply("CREDIT")).isNotEqualTo(id);
    assertThat(count("transactions WHERE operation='CARD_REJECT'")).isEqualTo(1);
    noMoneyMoved();
  }

  @Test void customerControlsArePersistedIdempotentOwnedAndCannotUndoBankRestriction() throws Exception {
    String id = apply("DEBIT");
    postJson(CARDS + "/" + id + "/block", other, "CUSTOMER", Map.of(), 404);
    assertThat(postJson(CARDS + "/" + id + "/block", customer, "CUSTOMER", Map.of(), 200).path("status").asText()).isEqualTo("BLOCKED");
    postJson(CARDS + "/" + id + "/block", customer, "CUSTOMER", Map.of(), 200);
    assertThat(count("transactions WHERE operation='CARD_BLOCK'")).isEqualTo(1);
    assertThat(db.queryForObject("SELECT status FROM accounts WHERE product_id=?", String.class, id)).isEqualTo("ACTIVE");
    db.update("UPDATE accounts SET status='BLOCKED' WHERE product_id=?", id);
    postJson(CARDS + "/" + id + "/unblock", customer, "CUSTOMER", Map.of(), 409);
    db.update("UPDATE accounts SET status='ACTIVE' WHERE product_id=?", id);
    postJson(CARDS + "/" + id + "/unblock", customer, "CUSTOMER", Map.of(), 200);
    postJson(CARDS + "/" + id + "/unblock", customer, "CUSTOMER", Map.of(), 200);
    assertThat(count("transactions WHERE operation='CARD_UNBLOCK'")).isEqualTo(1);
    db.update("UPDATE accounts SET status='BLOCKED' WHERE product_id=?", id);
    assertThat(getJson(CARDS + "/" + id, customer, "CUSTOMER").path("status").asText()).isEqualTo("BLOCKED");
    postJson(CARDS + "/" + id + "/block", customer, "CUSTOMER", Map.of(), 409);
    noMoneyMoved();
  }

  @Test void authorizationRechecksCurrentRolesAndRejectsForeignOrIneligibleFunding() throws Exception {
    var body = application(funding, "DEBIT", UUID.randomUUID().toString());
    mvc.perform(post(CARDS + "/applications").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isUnauthorized());
    postJson(CARDS + "/applications", other, "CUSTOMER", body, 400);
    postJson(CARDS + "/applications", admin, "ADMIN", body, 403);
    String id = apply("CREDIT");
    for (String role : List.of("SUPPORT_AGENT", "FRAUD_ANALYST", "CUSTOMER")) {
      long staff = customer(role);
      postJson(ADMIN + "/" + id + "/approve", staff, "ADMIN", Map.of("creditLimit", 2000), 403);
    }
    db.update("UPDATE customers SET status='LOCKED' WHERE id=?", admin);
    postJson(ADMIN + "/" + id + "/approve", admin, "ADMIN", Map.of("creditLimit", 2000), 401);
    for (String bankState : List.of("BLOCKED", "CLOSED")) {
      long a = account(customer, "SAVINGS", bankState, "INR", "0");
      postJson(CARDS + "/applications", customer, "CUSTOMER", application(a, "DEBIT", UUID.randomUUID().toString()), 400);
    }
    long usd = account(customer, "SAVINGS", "ACTIVE", "USD", "0");
    postJson(CARDS + "/applications", customer, "CUSTOMER", application(usd, "DEBIT", UUID.randomUUID().toString()), 400);
    noMoneyMoved();
  }

  @Test void requestIdentityAndCreditLimitsAreValidated() throws Exception {
    postJson(CARDS + "/applications", customer, "CUSTOMER", application(funding, "DEBIT", "bad-key"), 400);
    String key = UUID.randomUUID().toString();
    postJson(CARDS + "/applications", customer, "CUSTOMER", application(funding, "DEBIT", key), 200);
    postJson(CARDS + "/applications", customer, "CUSTOMER", application(funding, "CREDIT", key), 409);
    String id = apply("CREDIT");
    for (String limit : List.of("0", "-1", "1.001", "10000000000000"))
      postJson(ADMIN + "/" + id + "/approve", admin, "ADMIN", Map.of("creditLimit", new BigDecimal(limit)), 400);
    assertThat(getJson(CARDS + "/" + id, customer, "CUSTOMER").path("status").asText()).isEqualTo("PENDING_APPROVAL");
  }

  @Test void approvalCannotReopenClosedOrIndependentlyRestrictedCardAccount() throws Exception {
    String id = apply("CREDIT");
    db.update("UPDATE accounts SET status='CLOSED' WHERE product_id=?", id);
    postJson(ADMIN + "/" + id + "/approve", admin, "ADMIN", Map.of("creditLimit", 2000), 409);
    db.update("UPDATE accounts SET status='BLOCKED' WHERE product_id=?", id);
    db.update("INSERT INTO transactions(id,record_kind,user_id,source_account_id,operation,status,after_status)"
        + " SELECT ?,'ADMIN_EVENT',?,id,'UPDATE_ACCOUNT','COMPLETED','BLOCKED' FROM accounts WHERE product_id=?",
        "A-" + UUID.randomUUID(), userId(admin), id);
    postJson(ADMIN + "/" + id + "/approve", admin, "ADMIN", Map.of("creditLimit", 2000), 409);
    db.update("UPDATE accounts SET status='ACTIVE' WHERE product_id=?", id);
    postJson(ADMIN + "/" + id + "/approve", admin, "ADMIN", Map.of("creditLimit", 2000), 200);
    noMoneyMoved();
  }

  @Test void bothAdminAccountEditRoutesReleaseClosedCardSlotsAndPreventReopening() throws Exception {
    for (String route : List.of("/api/v1/admin/accounts/", "/api/accounts/")) {
      long source = account(customer, "SAVINGS", "ACTIVE", "INR", "0");
      String id = postJson(CARDS + "/applications", customer, "CUSTOMER",
          application(source, "DEBIT", UUID.randomUUID().toString()), 200).path("id").asText();
      long internal = db.queryForObject("SELECT id FROM accounts WHERE product_id=?", Long.class, id);
      editCard(route, internal, "CLOSED", 200);
      assertThat(getJson(CARDS + "/" + id, customer, "CUSTOMER").path("status").asText()).isEqualTo("CLOSED");
      assertThat(getJson(ADMIN + "/" + id, admin, "ADMIN").path("status").asText()).isEqualTo("CLOSED");
      assertThat(db.queryForObject("SELECT card_slot_key FROM accounts WHERE id=?", String.class, internal)).isNull();
      String replacement = postJson(CARDS + "/applications", customer, "CUSTOMER",
          application(source, "DEBIT", UUID.randomUUID().toString()), 200).path("id").asText();
      assertThat(replacement).isNotEqualTo(id);
      editCard(route, internal, "ACTIVE", 400);
    }
    noMoneyMoved();
  }

  @Test void genericEditsKeepRejectedCreditApplicationDecision() throws Exception {
    String id = apply("CREDIT");
    postJson(ADMIN + "/" + id + "/reject", admin, "ADMIN", Map.of("reason", "Eligibility not met"), 200);
    long internal = db.queryForObject("SELECT id FROM accounts WHERE product_id=?", Long.class, id);
    for (String route : List.of("/api/v1/admin/accounts/", "/api/accounts/")) {
      editCard(route, internal, "CLOSED", 200);
      assertThat(getJson(ADMIN + "/" + id, admin, "ADMIN").path("status").asText()).isEqualTo("REJECTED");
    }
    noMoneyMoved();
  }

  @Test void simultaneousDuplicateRequestsCreateExactlyOneCard() throws Exception {
    String key = UUID.randomUUID().toString();
    var payload = application(funding, "DEBIT", key);
    var statuses = parallel(List.of(() -> responseStatus(CARDS + "/applications", customer, "CUSTOMER", payload),
        () -> responseStatus(CARDS + "/applications", customer, "CUSTOMER", payload)));
    assertThat(statuses).containsExactlyInAnyOrder(200, 200);
    assertThat(count("accounts WHERE account_type='CARD'")).isEqualTo(1);
    assertThat(count("transactions WHERE operation='CARD_APPLICATION'")).isEqualTo(1);
  }

  @Test void simultaneousDifferentApplicationsCannotCreateTwoUsableDebitCards() throws Exception {
    var statuses = parallel(List.of(
        () -> responseStatus(CARDS + "/applications", customer, "CUSTOMER", application(funding, "DEBIT", UUID.randomUUID().toString())),
        () -> responseStatus(CARDS + "/applications", customer, "CUSTOMER", application(funding, "DEBIT", UUID.randomUUID().toString()))));
    assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    assertThat(count("accounts WHERE account_type='CARD'")).isEqualTo(1);
  }

  @Test void simultaneousConflictingAdminDecisionsHaveOneWinner() throws Exception {
    String id = apply("CREDIT");
    long secondAdmin = customer("ADMIN");
    var statuses = parallel(List.of(
        () -> responseStatus(ADMIN + "/" + id + "/approve", admin, "ADMIN", Map.of("creditLimit", 2000)),
        () -> responseStatus(ADMIN + "/" + id + "/reject", secondAdmin, "ADMIN", Map.of("reason", "Eligibility declined"))));
    assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    assertThat(count("transactions WHERE operation IN ('CARD_APPROVE','CARD_REJECT')")).isEqualTo(1);
    noMoneyMoved();
  }

  @Test void savedPayeeMandateDerivesRecipientAndExecutesOnceInBusinessTimezone() throws Exception {
    String payee = payee(customer, destination, "ACTIVE", true);
    JsonNode mandate = postJson("/api/v1/mandates", customer, "CUSTOMER", mandate(payee), 200);
    String id = mandate.path("ID").asText();
    assertThat(mandate.path("TARGET_ID").asText()).isEqualTo(payee);
    assertThat(mandate.path("DESTINATION_ACCOUNT_ID").asLong()).isEqualTo(destination);
    assertThat(mandate.path("DISPLAY_NAME").asText()).isEqualTo("Saved utility recipient");
    noMoneyMoved();
    postJson("/api/v1/mandates/" + id + "/activate", customer, "CUSTOMER", Map.of(), 200);
    var execute = Map.of("amount", 30, "requestId", UUID.randomUUID().toString());
    postJson("/api/v1/mandates/" + id + "/execute", customer, "CUSTOMER", execute, 200);
    postJson("/api/v1/mandates/" + id + "/execute", customer, "CUSTOMER", execute, 200);
    assertThat(balance(funding)).isEqualByComparingTo("970.00");
    assertThat(balance(destination)).isEqualByComparingTo("230.00");
    assertThat(count("journal_entries")).isEqualTo(1);
    assertThat(count("ledger_entries")).isEqualTo(2);
  }

  @Test void savedPayeeMandatesRejectForeignExternalUnlinkedUnverifiedAndBlockedRecipients() throws Exception {
    String owned = payee(customer, destination, "ACTIVE", true);
    postJson("/api/v1/mandates", other, "CUSTOMER", Map.of("sourceAccountId", destination, "payeeId", owned,
        "limit", 100, "startDate", "2026-09-27"), 400);
    for (String invalid : List.of("EP-external-recipient", "missing-recipient"))
      postJson("/api/v1/mandates", customer, "CUSTOMER", mandate(invalid), 400);
    db.update("UPDATE transactions SET destination_hash=NULL WHERE id=?", owned);
    postJson("/api/v1/mandates", customer, "CUSTOMER", mandate(owned), 400);
    db.update("UPDATE transactions SET destination_hash=?,status='BLOCKED' WHERE id=?", fingerprint(destination), owned);
    postJson("/api/v1/mandates", customer, "CUSTOMER", mandate(owned), 400);
    db.update("UPDATE transactions SET status='ACTIVE',destination_account_id=NULL WHERE id=?", owned);
    postJson("/api/v1/mandates", customer, "CUSTOMER", mandate(owned), 400);
    db.update("UPDATE transactions SET destination_account_id=? WHERE id=?", destination, owned);
    db.update("UPDATE accounts SET status='BLOCKED' WHERE id=?", destination);
    postJson("/api/v1/mandates", customer, "CUSTOMER", mandate(owned), 400);
    assertThat(count("transactions WHERE record_kind='MANDATE'")).isZero();
    noMoneyMoved();
  }

  @Test void savedPayeeIsRecheckedBeforeActivationAndPaymentAndCannotBeRetargeted() throws Exception {
    String saved = payee(customer, destination, "ACTIVE", true);
    String id = postJson("/api/v1/mandates", customer, "CUSTOMER", mandate(saved), 200).path("ID").asText();
    db.update("UPDATE transactions SET status='BLOCKED' WHERE id=?", saved);
    postJson("/api/v1/mandates/" + id + "/activate", customer, "CUSTOMER", Map.of(), 400);
    db.update("UPDATE transactions SET status='ACTIVE' WHERE id=?", saved);
    postJson("/api/v1/mandates/" + id + "/activate", customer, "CUSTOMER", Map.of(), 200);
    long changed = account(other, "CURRENT", "ACTIVE", "INR", "0");
    db.update("UPDATE transactions SET destination_account_id=?,destination_hash=? WHERE id=?", changed, fingerprint(changed), saved);
    postJson("/api/v1/mandates/" + id + "/execute", customer, "CUSTOMER", Map.of("amount", 30, "requestId", UUID.randomUUID().toString()), 409);
    noMoneyMoved();
  }

  @Test void manualMandatesRemainSupportedAndCannotMixSavedAndManualDestination() throws Exception {
    String saved = payee(customer, destination, "ACTIVE", true);
    var mixed = new java.util.HashMap<String, Object>(mandate(saved));
    mixed.put("beneficiaryAccountId", destination);
    postJson("/api/v1/mandates", customer, "CUSTOMER", mixed, 400);
    var manual = Map.of("sourceAccountId", funding, "beneficiaryAccountId", destination,
        "payee", "Manual recipient", "limit", 100, "startDate", "2026-09-27");
    JsonNode created = postJson("/api/v1/mandates", customer, "CUSTOMER", manual, 200);
    assertThat(created.path("TARGET_ID").isNull()).isTrue();
    assertThat(created.path("DESTINATION_ACCOUNT_ID").asLong()).isEqualTo(destination);
    noMoneyMoved();
  }

  private String apply(String kind) throws Exception {
    return postJson(CARDS + "/applications", customer, "CUSTOMER", application(funding, kind, UUID.randomUUID().toString()), 200).path("id").asText();
  }
  private Map<String, Object> application(long account, String kind, String key) {
    return Map.of("accountId", account, "cardType", kind, "requestId", key, "displayName", "My local card");
  }
  private Map<String, Object> mandate(String payeeId) {
    return Map.of("sourceAccountId", funding, "payeeId", payeeId, "limit", 100,
        "startDate", "2026-09-27", "endDate", "2026-10-27");
  }
  private JsonNode postJson(String path, long who, String role, Object body, int expected) throws Exception {
    var response = mvc.perform(post(path).header("Authorization", authorization(who, role)).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(body))).andExpect(status().is(expected)).andReturn().getResponse();
    return json.readTree(response.getContentAsString());
  }
  private int responseStatus(String path, long who, String role, Object body) throws Exception {
    return mvc.perform(post(path).header("Authorization", authorization(who, role)).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();
  }
  private JsonNode getJson(String path, long who, String role) throws Exception {
    return json.readTree(mvc.perform(get(path).header("Authorization", authorization(who, role)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }
  private void editCard(String route, long id, String state, int expected) throws Exception {
    var body = new java.util.HashMap<String, Object>();
    body.put(route.startsWith("/api/v1/") ? "name" : "accountName", "My local card");
    body.put("status", state);
    if (route.startsWith("/api/v1/")) {
      body.put("version", db.queryForObject("SELECT version FROM accounts WHERE id=?", Long.class, id));
      body.put("reason", "Synthetic card lifecycle review");
    }
    mvc.perform(put(route + id).header("Authorization", authorization(admin, "ADMIN"))
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().is(expected));
  }
  private List<Integer> parallel(List<Callable<Integer>> work) throws Exception {
    var pool = Executors.newFixedThreadPool(work.size());
    try {
      var start = new CountDownLatch(1);
      var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
      for (var call : work) futures.add(pool.submit(() -> { start.await(); return call.call(); }));
      start.countDown();
      var results = new ArrayList<Integer>();
      for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
      return results;
    } finally { pool.shutdownNow(); }
  }
  private void noMoneyMoved() {
    assertThat(balance(funding)).isEqualByComparingTo("1000.00");
    assertThat(balance(destination)).isEqualByComparingTo("200.00");
    assertThat(count("journal_entries")).isZero();
    assertThat(count("ledger_entries")).isZero();
    assertThat(count("transactions WHERE record_kind='PAYMENT'")).isZero();
  }
  private BigDecimal balance(long id) { return db.queryForObject("SELECT balance FROM accounts WHERE id=?", BigDecimal.class, id); }
  private int count(String scope) { return db.queryForObject("SELECT COUNT(*) FROM " + scope, Integer.class); }
  private String authorization(long id, String role) { return "Bearer " + jwt.issue(userId(id), "fixture@example.test", role).value(); }
  private String userId(long id) { return db.queryForObject("SELECT user_id FROM customers WHERE id=?", String.class, id); }
  private long customer(String role) {
    String id = "cards-user-" + (++serial);
    db.update("INSERT INTO customers(user_id,full_name,email,status,role,created_at,updated_at)"
        + " VALUES(?,? ,?,'ACTIVE',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, "Synthetic Person", id + "@example.test", role);
    db.update("INSERT INTO customer_credentials(id,user_id,credential_type,password_hash) VALUES(?,?,'PASSWORD','synthetic-fixture-hash')", id, id);
    return db.queryForObject("SELECT id FROM customers WHERE user_id=?", Long.class, id);
  }
  private long account(long owner, String type, String status, String currency, String balance) {
    String number = "card-account-" + (++serial);
    db.update("INSERT INTO accounts(account_number,customer_id,account_name,account_type,account_category,currency_code,balance,status,created_at,updated_at)"
        + " VALUES(?,?,?,?,'CUSTOMER',?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", number, owner, "Synthetic account", type, currency, new BigDecimal(balance), status);
    return db.queryForObject("SELECT id FROM accounts WHERE account_number=?", Long.class, number);
  }
  private String payee(long owner, long account, String status, boolean verified) throws Exception {
    String id = "P-" + UUID.randomUUID();
    db.update("INSERT INTO transactions(id,record_kind,user_id,destination_account_id,display_name,status,destination_hash)"
        + " VALUES(?,'BENEFICIARY',?,?,'Saved utility recipient',?,?)", id, userId(owner), account, status, verified ? fingerprint(account) : null);
    return id;
  }
  private String fingerprint(long account) throws Exception {
    return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
        .digest(("NEXA:" + account).getBytes(StandardCharsets.UTF_8)));
  }
  private static Connection initialize() {
    try {
      var connection = AccountApplicationTestDatabase.initializedExternalTransferDatabase();
      ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V31__local_card_applications.sql"));
      return connection;
    } catch (Exception error) { throw new ExceptionInInitializerError(error); }
  }
  @TestConfiguration static class FixedTime { @Bean @Primary Clock cardsClock() { return Clock.fixed(NOW, ZoneOffset.UTC); } }
}
