package com.nexa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexa.api.NexaApiApplication;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Authenticated request/replay tests against an isolated in-memory database; no Oracle access. */
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
    "nexa.security.jwt.secret=isolated-mandate-application-secret-with-more-than-32-bytes",
    "nexa.security.jwt.issuer=isolated-mandate-applications", "nexa.cors.allowed-origins=http://localhost:8000"
})
@ActiveProfiles("integration")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthenticatedMandateApplicationsIntegrationTest {
  private static final String BASE = "/api/v1/mandates";
  private static final Connection KEEP_ALIVE = initialize();
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired com.nexa.api.security.JwtService jwt;
  private long customer, other, funding, destination;
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
    db.update("DELETE FROM accounts WHERE account_category='CUSTOMER' AND account_type IN ('CARD','LOAN')");
    db.update("DELETE FROM accounts WHERE account_category='CUSTOMER'");
    db.update("DELETE FROM customers");
    serial = 0;
    customer = customer(); other = customer();
    funding = account(customer, "1000.00"); destination = account(other, "200.00");
  }
  @AfterAll static void closeKeeper() throws Exception { KEEP_ALIVE.close(); }

  @Test void replayAndLookupRecoverOnePendingMandateWithoutMovingMoney() throws Exception {
    String key = key(); var payload = manual(key, funding, destination);
    var first = create(customer, payload, 200);
    assertThat(first.path("STATUS").asText()).isEqualTo("PENDING");
    assertThat(first.path("APPLICATION_KEY").asText()).isEqualTo(key);
    payload.put("limit", "700.0");
    assertThat(create(customer, payload, 200).path("ID").asText()).isEqualTo(first.path("ID").asText());
    var result = mvc.perform(get(BASE + "/applications/by-request/" + key).header("Authorization", auth(customer)))
        .andExpect(status().isOk()).andReturn().getResponse();
    assertThat(result.getHeader("Cache-Control")).contains("no-store");
    assertThat(json.readTree(result.getContentAsString()).path("ID").asText()).isEqualTo(first.path("ID").asText());
    assertThat(count("transactions WHERE record_kind='MANDATE'")).isEqualTo(1);
    assertThat(count("transactions WHERE record_kind='MANDATE_EVENT'")).isEqualTo(1);
    noMoneyMoved();
  }

  @Test void changedDetailsCannotReuseACompletedCreationKey() throws Exception {
    var original = manual(key(), funding, destination);
    create(customer, original, 200);
    for (var replacement : List.of(Map.entry("limit", (Object) "701.00"),
        Map.entry("payee", (Object) "Different recipient label"),
        Map.entry("startDate", (Object) "2030-10-02"), Map.entry("endDate", (Object) "2030-12-31"))) {
      var changed = new LinkedHashMap<>(original); changed.put(replacement.getKey(), replacement.getValue());
      create(customer, changed, 409);
    }
    assertThat(count("transactions WHERE record_kind='MANDATE'")).isEqualTo(1);
    noMoneyMoved();
  }

  @Test void lookupAndApplicationKeysAreScopedToTheAuthenticatedOwner() throws Exception {
    String key = key(); var first = create(customer, manual(key, funding, destination), 200);
    mvc.perform(get(BASE + "/applications/by-request/" + key).header("Authorization", auth(other)))
        .andExpect(status().isNotFound());
    var second = create(other, manual(key, destination, funding), 200);
    assertThat(second.path("ID").asText()).isNotEqualTo(first.path("ID").asText());
    assertThat(second.path("USER_ID").asText()).isEqualTo(userId(other));
    mvc.perform(get(BASE + "/applications/by-request/" + key)).andExpect(status().isUnauthorized());
    create(other, manual(key(), funding, destination), 404);
    noMoneyMoved();
  }

  @Test void concurrentConfirmationsOfCreationProduceOneMandateAndOneEvent() throws Exception {
    var payload = manual(key(), funding, destination); var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      java.util.concurrent.Callable<String> call = () -> {
        start.await(10, TimeUnit.SECONDS); return create(customer, payload, 200).path("ID").asText();
      };
      var first = pool.submit(call); var second = pool.submit(call); start.countDown();
      assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
    } finally { pool.shutdownNow(); }
    assertThat(count("transactions WHERE record_kind='MANDATE'")).isEqualTo(1);
    assertThat(count("transactions WHERE record_kind='MANDATE_EVENT'")).isEqualTo(1);
    noMoneyMoved();
  }

  @Test void savedPayeeReplayReturnsOriginalRequestAfterPayeeStateChanges() throws Exception {
    String payee = "P-" + UUID.randomUUID();
    String fingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
        .digest(("NEXA:" + destination).getBytes(StandardCharsets.UTF_8)));
    db.update("INSERT INTO transactions(id,record_kind,user_id,destination_account_id,display_name,status,destination_hash)"
        + " VALUES(?,'BENEFICIARY',?,?,'Utility recipient','ACTIVE',?)", payee, userId(customer), destination, fingerprint);
    var payload = manual(key(), funding, destination); payload.remove("beneficiaryAccountId"); payload.remove("payee");
    payload.put("payeeId", payee);
    var first = create(customer, payload, 200);
    db.update("UPDATE transactions SET status='SUSPENDED' WHERE id=?", payee);
    var replay = create(customer, payload, 200);
    assertThat(replay.path("ID").asText()).isEqualTo(first.path("ID").asText());
    assertThat(replay.path("DISPLAY_NAME").asText()).isEqualTo("Utility recipient");
    assertThat(count("transactions WHERE record_kind='MANDATE'")).isEqualTo(1);
    noMoneyMoved();
  }

  @Test void anInvalidAttemptDoesNotConsumeItsKeyAndLegacyCreationStillWorks() throws Exception {
    var payload = manual(key(), funding, destination); payload.put("limit", "-1"); create(customer, payload, 400);
    payload.put("limit", "700.00"); create(customer, payload, 200);
    var legacy = manual(key(), funding, destination); legacy.remove("applicationKey");
    assertThat(create(customer, legacy, 200).path("STATUS").asText()).isEqualTo("PENDING");
    assertThat(count("transactions WHERE record_kind='MANDATE'")).isEqualTo(2);
    noMoneyMoved();
  }

  private Map<String, Object> manual(String key, long source, long recipient) {
    return new LinkedHashMap<>(Map.of("applicationKey", key, "sourceAccountId", source,
        "beneficiaryAccountId", recipient, "payee", "Utility recipient", "limit", "700.00", "startDate", "2030-10-01"));
  }
  private String key() { return "chat-mandate-" + UUID.randomUUID(); }
  private JsonNode create(long owner, Map<String, Object> payload, int expected) throws Exception {
    var result = mvc.perform(post(BASE).header("Authorization", auth(owner)).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(payload))).andExpect(status().is(expected)).andReturn().getResponse();
    return json.readTree(result.getContentAsString());
  }
  private void noMoneyMoved() {
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", BigDecimal.class, funding)).isEqualByComparingTo("1000.00");
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", BigDecimal.class, destination)).isEqualByComparingTo("200.00");
    assertThat(count("journal_entries")).isZero(); assertThat(count("ledger_entries")).isZero();
    assertThat(count("transactions WHERE record_kind='PAYMENT'")).isZero();
  }
  private int count(String scope) { return db.queryForObject("SELECT COUNT(*) FROM " + scope, Integer.class); }
  private String auth(long owner) { return "Bearer " + jwt.issue(userId(owner), "fixture@example.test", "CUSTOMER").value(); }
  private String userId(long owner) { return db.queryForObject("SELECT user_id FROM customers WHERE id=?", String.class, owner); }
  private long customer() {
    String id = "mandate-user-" + (++serial);
    db.update("INSERT INTO customers(user_id,full_name,email,status,role,created_at,updated_at)"
        + " VALUES(?,? ,?,'ACTIVE','CUSTOMER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, "Synthetic Person", id + "@example.test");
    db.update("INSERT INTO customer_credentials(id,user_id,credential_type,password_hash) VALUES(?,?,'PASSWORD','synthetic-fixture-hash')", id, id);
    return db.queryForObject("SELECT id FROM customers WHERE user_id=?", Long.class, id);
  }
  private long account(long owner, String balance) {
    String number = "mandate-account-" + (++serial);
    db.update("INSERT INTO accounts(account_number,customer_id,account_name,account_type,account_category,currency_code,balance,status,created_at,updated_at)"
        + " VALUES(?,?,'Synthetic account','SAVINGS','CUSTOMER','INR',?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", number, owner, new BigDecimal(balance));
    return db.queryForObject("SELECT id FROM accounts WHERE account_number=?", Long.class, number);
  }
  private static Connection initialize() {
    try {
      var connection = AccountApplicationTestDatabase.initializedExternalTransferDatabase();
      ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V31__local_card_applications.sql"));
      return connection;
    } catch (Exception error) { throw new ExceptionInInitializerError(error); }
  }
}
