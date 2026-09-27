package com.nexa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexa.api.NexaApiApplication;
import com.nexa.api.external.ExternalPayoutProvider;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Real HTTP/JWT/JDBC/JPA with a fake provider. No network calls, live accounts or bank funds. */
@SpringBootTest(classes = NexaApiApplication.class, properties = {
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.default_schema=PUBLIC", "spring.jpa.open-in-view=false",
    "spring.jpa.show-sql=false", "logging.level.root=WARN",
    "spring.datasource.hikari.schema=PUBLIC", "spring.datasource.hikari.connection-init-sql=SELECT 1",
    "spring.sql.init.mode=never", "spring.config.import=", "nexa.onboarding.enabled=false",
    "nexa.onboarding.documents.enabled=false", "nexa.onboarding.documents.root=",
    "nexa.onboarding.identity.key-id=", "nexa.onboarding.identity.encryption-key=",
    "nexa.payouts.account-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "nexa.payouts.mode=DISABLED", "nexa.payouts.client-id=", "nexa.payouts.client-secret=",
    "nexa.admin.bootstrap.email=", "nexa.admin.bootstrap.password=",
    "nexa.security.jwt.secret=isolated-http-external-transfer-secret-with-more-than-32-bytes",
    "nexa.security.jwt.issuer=isolated-http-external-transfer", "nexa.ai.enabled=false",
    "nexa.cors.allowed-origins=http://localhost:8000"
})
@ActiveProfiles("integration")
@AutoConfigureMockMvc
@Import(AuthenticatedExternalTransferIntegrationTest.FakeConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthenticatedExternalTransferIntegrationTest {
  private static final String API = "/api/v1/external-transfers";
  private static final String ACCOUNT = "123456789012";
  private static final String IFSC = "TEST0123456";
  private static final Connection KEEP_ALIVE = initialize();
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired FakeProvider provider;
  @Autowired com.nexa.api.security.JwtService jwt;
  private Session owner;
  private long source;
  private String payee;

  @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> {
      try { return KEEP_ALIVE.getMetaData().getURL() + ";MODE=Oracle;LOCK_TIMEOUT=10000"; }
      catch (Exception error) { throw new IllegalStateException(error); }
    });
    properties.add("spring.datasource.username", () -> "sa");
    properties.add("spring.datasource.password", () -> "");
    properties.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
  }

  @BeforeEach void setup() throws Exception {
    try (Connection connection = Objects.requireNonNull(db.getDataSource()).getConnection()) {
      assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:account-applications-");
    }
    for (String table : List.of("external_transfer_reviews", "external_bank_payees", "bill_payment_attempts",
        "ledger_entries", "journal_entries", "transactions", "customer_credentials")) db.update("DELETE FROM " + table);
    db.update("DELETE FROM accounts WHERE account_category='CUSTOMER'");
    db.update("DELETE FROM customers");
    provider.reset();
    owner = register();
    long customer = db.queryForObject("SELECT id FROM customers WHERE user_id=?", Long.class, owner.id());
    db.update("INSERT INTO accounts(account_number,customer_id,account_name,account_type,account_category,currency_code,"
        + "balance,status,created_at,updated_at) VALUES('900000123456',?,'Sandbox source','SAVINGS','CUSTOMER','INR',2000,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", customer);
    source = db.queryForObject("SELECT id FROM accounts WHERE account_number='900000123456'", Long.class);
    payee = write("/api/v1/external-payees", owner, Map.of("displayName", "Sandbox recipient",
        "recipientName", "Synthetic Recipient", "bankName", "Fixture bank", "accountNumber", ACCOUNT,
        "accountNumberConfirmation", ACCOUNT, "ifsc", IFSC), 201).get("id").asText();
    db.update("INSERT INTO transactions(id,record_kind,user_id,display_name,amount,currency_code,status,category)"
        + " VALUES('fixture-unrelated-bill','BILL',?,'Unrelated bill',1000,'INR','UPCOMING','Utilities')", owner.id());
  }

  @AfterAll static void closeKeeper() throws Exception { KEEP_ALIVE.close(); }

  @Test void sandboxCompletionIsDurableMaskedAndNeverChangesNexaMoneyOrBills() throws Exception {
    assertThat(read(API + "/readiness", owner).get("ready").asBoolean()).isTrue();
    var body = prepareBody();
    var review = write(API + "/prepare", owner, body, 200);
    String id = review.get("id").asText();
    assertThat(id).isEqualTo("XT-" + body.get("requestKey"));
    assertThat(review.get("environment").asText()).isEqualTo("SANDBOX");
    assertThat(review.get("provider").asText()).isEqualTo("CASHFREE");
    assertThat(review.toString()).doesNotContain(ACCOUNT);
    assertThat(write(API + "/prepare", owner, body, 200).get("id").asText()).isEqualTo(id);
    var completed = confirm(id);
    assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(completed.get("completedAt").asText()).isNotBlank();
    assertThat(completed.get("providerTransferId").asText()).isEqualTo("CF123");
    assertThat(completed.get("utr").asText()).isEqualTo("UTR123");
    assertThat(confirm(id).get("status").asText()).isEqualTo("COMPLETED");
    assertThat(write(API + "/prepare", owner, body, 200).get("status").asText()).isEqualTo("COMPLETED");
    assertThat(write(API + "/" + id + "/cancel", owner, Map.of(), 200).get("status").asText()).isEqualTo("COMPLETED");
    assertThat(read(API + "?payeeId=" + payee, owner).size()).isEqualTo(1);
    assertThat(provider.submissions.get()).isEqualTo(1);
    assertThat(provider.requests.keySet()).containsExactly("XT_" + body.get("requestKey").toString().replace("-", ""));
    var changed = new LinkedHashMap<>(body); changed.put("amount", "20.00");
    write(API + "/prepare", owner, changed, 409);
    noMoneyMoved();
  }

  @Test void concurrentConfirmsObserveCommittedClaimAndOnlyOneProviderPost() throws Exception {
    String id = prepare();
    provider.entered = new CountDownLatch(1); provider.release = new CountDownLatch(1);
    var pool = Executors.newSingleThreadExecutor();
    try {
      var first = pool.submit(() -> confirm(id));
      assertThat(provider.entered.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(read(API + "/" + id, owner).get("status").asText()).isEqualTo("SUBMITTING");
      assertThat(confirm(id).get("status").asText()).isEqualTo("SUBMITTING");
      assertThat(provider.submissions.get()).isEqualTo(1);
      provider.release.countDown();
      assertThat(first.get(10, TimeUnit.SECONDS).get("status").asText()).isEqualTo("COMPLETED");
    } finally { provider.release.countDown(); pool.shutdownNow(); }
    noMoneyMoved();
  }

  @Test void uncertainPostIsRecoveredOnlyByAuthenticatedGetWithoutResubmitting() throws Exception {
    provider.submission = request -> { throw new IllegalStateException("Raw provider secret " + ACCOUNT); };
    String id = prepare();
    var pending = confirm(id);
    assertThat(pending.get("status").asText()).isEqualTo("PENDING");
    assertThat(pending.toString()).doesNotContain("Raw provider secret", ACCOUNT);
    assertThat(confirm(id).get("status").asText()).isEqualTo("PENDING");
    provider.lookup = key -> new ExternalPayoutProvider.Outcome(ExternalPayoutProvider.State.NOT_FOUND,
        null, null, null, null, "Do not expose this provider text", key, null, null, null);
    assertThat(refresh(id).get("status").asText()).isEqualTo("PENDING");
    assertThat(confirm(id).get("status").asText()).isEqualTo("PENDING");
    // Official GET may omit beneficiary details. Immutable transfer ID still identifies the request.
    provider.lookup = key -> new ExternalPayoutProvider.Outcome(ExternalPayoutProvider.State.SUCCESS,
        "CF123", "SUCCESS", "COMPLETED", "UTR123", "raw message", key, null, null, null);
    assertThat(refresh(id).get("status").asText()).isEqualTo("COMPLETED");
    assertThat(provider.submissions.get()).isEqualTo(1);
    assertThat(provider.lookups.get()).isEqualTo(2);
    noMoneyMoved();
  }

  @Test void postWithoutOptionalReceiptDetailsWaitsForLookupAndBindsProviderId() throws Exception {
    provider.submission = request -> new ExternalPayoutProvider.Outcome(ExternalPayoutProvider.State.SUCCESS,
        "CF_FIRST", "SUCCESS", "COMPLETED", null, null, request.transferId(), null, null, null);
    String id = prepare();
    var pending = confirm(id);
    assertThat(pending.get("status").asText()).isEqualTo("PENDING");
    assertThat(pending.get("providerTransferId").asText()).isEqualTo("CF_FIRST");
    provider.lookup = key -> new ExternalPayoutProvider.Outcome(ExternalPayoutProvider.State.SUCCESS,
        "CF_DIFFERENT", "SUCCESS", "COMPLETED", null, null, key, null, null, null);
    assertThat(refresh(id).get("status").asText()).isEqualTo("PENDING");
    provider.lookup = key -> new ExternalPayoutProvider.Outcome(ExternalPayoutProvider.State.SUCCESS,
        "CF_FIRST", "SUCCESS", "COMPLETED", null, null, key, null, null, null);
    assertThat(refresh(id).get("status").asText()).isEqualTo("COMPLETED");
    noMoneyMoved();
  }

  @Test void mismatchedProviderIdAmountAccountOrIfscNeverCompletes() throws Exception {
    for (String mismatch : List.of("id", "amount", "account", "ifsc")) {
      provider.submission = request -> new ExternalPayoutProvider.Outcome(ExternalPayoutProvider.State.SUCCESS,
          "CF123", "SUCCESS", "COMPLETED", "UTR123", null,
          mismatch.equals("id") ? "ANOTHER_TRANSFER" : request.transferId(),
          mismatch.equals("amount") ? new BigDecimal("999.00") : request.amount(),
          mismatch.equals("account") ? "999999999999" : request.bankAccountNumber(),
          mismatch.equals("ifsc") ? "WRNG0123456" : request.ifsc());
      String id = prepare();
      assertThat(confirm(id).get("status").asText()).isEqualTo("PENDING");
      var bad = provider.submission;
      provider.lookup = key -> bad.apply(provider.requests.get(key));
      assertThat(refresh(id).get("status").asText()).isEqualTo("PENDING");
      assertThat(confirm(id).get("status").asText()).isEqualTo("PENDING");
    }
    assertThat(provider.submissions.get()).isEqualTo(4);
    noMoneyMoved();
  }

  @Test void sentToBeneficiaryIsPendingAndMatchedReversalSupersedesCompletion() throws Exception {
    provider.submission = request -> new ExternalPayoutProvider.Outcome(ExternalPayoutProvider.State.SUCCESS,
        "CF123", "SUCCESS", "SENT_TO_BENEFICIARY", null, null, request.transferId(),
        request.amount(), request.bankAccountNumber(), request.ifsc());
    String id = prepare();
    assertThat(confirm(id).get("status").asText()).isEqualTo("PENDING");
    provider.lookup = key -> FakeProvider.success(provider.requests.get(key));
    assertThat(refresh(id).get("status").asText()).isEqualTo("COMPLETED");
    provider.lookup = key -> new ExternalPayoutProvider.Outcome(ExternalPayoutProvider.State.REVERSED,
        "CF123", "REVERSED", "REVERSED", "UTR123", null, key, null, null, null);
    assertThat(refresh(id).get("status").asText()).isEqualTo("REVERSED");
    assertThat(confirm(id).get("status").asText()).isEqualTo("REVERSED");
    assertThat(provider.submissions.get()).isEqualTo(1);
    noMoneyMoved();
  }

  @Test void failedProviderOutcomeIsTerminalAndRawMessageIsNeverReturned() throws Exception {
    provider.submission = request -> new ExternalPayoutProvider.Outcome(ExternalPayoutProvider.State.FAILED,
        "CF123", "FAILED", "REJECTED", null, "Bank account " + ACCOUNT + " secret", request.transferId(),
        request.amount(), request.bankAccountNumber(), request.ifsc());
    String id = prepare();
    var failed = confirm(id);
    assertThat(failed.get("status").asText()).isEqualTo("FAILED");
    assertThat(failed.toString()).doesNotContain(ACCOUNT, "secret");
    assertThat(confirm(id).get("status").asText()).isEqualTo("FAILED");
    assertThat(refresh(id).get("status").asText()).isEqualTo("FAILED");
    assertThat(provider.submissions.get()).isEqualTo(1); assertThat(provider.lookups.get()).isZero();
    noMoneyMoved();
  }

  @Test void disabledOrLiveProviderCannotCreateReviewsOrSubmitRequests() throws Exception {
    for (var environment : List.of(ExternalPayoutProvider.Environment.DISABLED, ExternalPayoutProvider.Environment.LIVE)) {
      provider.environment = environment;
      assertThat(read(API + "/readiness", owner).get("ready").asBoolean()).isFalse();
      write(API + "/prepare", owner, prepareBody(), 400);
    }
    provider.environment = ExternalPayoutProvider.Environment.SANDBOX;
    provider.available = false;
    write(API + "/prepare", owner, prepareBody(), 400);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM external_transfer_reviews", Long.class)).isZero();
    assertThat(provider.submissions.get()).isZero();
    noMoneyMoved();
  }

  @Test void recipientNameMustMatchTheNameShownInTheSavedReviewBeforeSubmission() throws Exception {
    String id = prepare();
    assertThat(read(API + "/" + id, owner).get("recipientName").asText()).isEqualTo("Synthetic Recipient");
    // Payees are immutable through the API; even a database correction must require a new review.
    db.update("UPDATE external_bank_payees SET recipient_name='Changed Recipient' WHERE id=?", payee);
    var result = confirm(id);
    assertThat(result.get("status").asText()).isEqualTo("FAILED");
    assertThat(result.get("failureReason").asText()).contains("recipient name changed");
    assertThat(result.get("recipientName").asText()).isEqualTo("Synthetic Recipient");
    assertThat(confirm(id).get("status").asText()).isEqualTo("FAILED");
    assertThat(provider.submissions.get()).isZero();
    noMoneyMoved();
  }

  @Test void cancelExpiryAndChangedEligibilityNeverSubmitToProvider() throws Exception {
    String cancelled = prepare();
    assertThat(write(API + "/" + cancelled + "/cancel", owner, Map.of(), 200).get("status").asText()).isEqualTo("CANCELLED");
    assertThat(confirm(cancelled).get("status").asText()).isEqualTo("CANCELLED");
    String expired = prepare();
    db.update("UPDATE external_transfer_reviews SET expires_at=? WHERE id=?", Timestamp.from(Instant.now().minusSeconds(10)), expired);
    assertThat(confirm(expired).get("status").asText()).isEqualTo("EXPIRED");
    String inactive = prepare();
    db.update("UPDATE accounts SET status='BLOCKED' WHERE id=?", source);
    assertThat(confirm(inactive).get("status").asText()).isEqualTo("FAILED");
    db.update("UPDATE accounts SET status='ACTIVE' WHERE id=?", source);
    String insufficient = prepare();
    db.update("UPDATE accounts SET balance=1 WHERE id=?", source);
    assertThat(confirm(insufficient).get("status").asText()).isEqualTo("FAILED");
    db.update("UPDATE accounts SET balance=2000 WHERE id=?", source);
    assertThat(confirm(insufficient).get("status").asText()).isEqualTo("FAILED");
    String inactivePayee = prepare();
    db.update("UPDATE external_bank_payees SET status='SUSPENDED' WHERE id=?", payee);
    assertThat(confirm(inactivePayee).get("status").asText()).isEqualTo("FAILED");
    assertThat(provider.submissions.get()).isZero();
    noMoneyMoved();
  }

  @Test void authorizationOwnershipAndAmountValidationApplyBeforeAnyProviderCall() throws Exception {
    mvc.perform(get(API + "/readiness")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/external-payees")).andExpect(status().isUnauthorized());
    String id = prepare();
    Session other = register();
    write(API + "/prepare", other, prepareBody(), 404);
    mvc.perform(get(API + "/" + id).header("Authorization", other.authorization())).andExpect(status().isNotFound());
    for (String operation : List.of("confirm", "refresh", "cancel"))
      write(API + "/" + id + "/" + operation, other, Map.of(), 404);
    mvc.perform(get(API + "?payeeId=" + payee).header("Authorization", other.authorization())).andExpect(status().isNotFound());
    Session viewer = new Session(owner.id(), "Bearer " + jwt.issue(owner.id(), "fixture@example.test", "VIEWER").value());
    write(API + "/prepare", viewer, prepareBody(), 403);
    for (String suffix : List.of("/readiness", "/" + id, "?payeeId=" + payee))
      mvc.perform(get(API + suffix).header("Authorization", viewer.authorization())).andExpect(status().isForbidden());
    for (String operation : List.of("confirm", "refresh", "cancel"))
      write(API + "/" + id + "/" + operation, viewer, Map.of(), 403);
    mvc.perform(get("/api/v1/external-payees").header("Authorization", viewer.authorization())).andExpect(status().isForbidden());
    for (String amount : List.of("0.99", "1.001", "2000.01", "10000000000000", "1E+2147483647")) {
      var invalid = prepareBody(); invalid.put("amount", amount);
      write(API + "/prepare", owner, invalid, 400);
    }
    assertThat(provider.submissions.get()).isZero();
    noMoneyMoved();
  }

  @Test void lateDatabaseFailurePreservesSubmissionClaimAndRecoveryNeverPostsAgain() throws Exception {
    String id = prepare();
    db.execute("ALTER TABLE external_transfer_reviews ADD CONSTRAINT h2_reject_external_complete CHECK(status<>'COMPLETED')");
    try {
      mvc.perform(post(API + "/" + id + "/confirm").header("Authorization", owner.authorization())
          .contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().is5xxServerError());
      assertThat(read(API + "/" + id, owner).get("status").asText()).isEqualTo("SUBMITTING");
      assertThat(confirm(id).get("status").asText()).isEqualTo("SUBMITTING");
      assertThat(provider.submissions.get()).isEqualTo(1);
    } finally { db.execute("ALTER TABLE external_transfer_reviews DROP CONSTRAINT h2_reject_external_complete"); }
    assertThat(refresh(id).get("status").asText()).isEqualTo("COMPLETED");
    assertThat(provider.submissions.get()).isEqualTo(1);
    noMoneyMoved();
  }

  private Map<String, Object> prepareBody() {
    var body = new LinkedHashMap<String, Object>();
    body.put("requestKey", UUID.randomUUID().toString()); body.put("sourceAccountId", Long.toString(source));
    body.put("payeeId", payee); body.put("amount", "10.00"); return body;
  }
  private String prepare() throws Exception { return write(API + "/prepare", owner, prepareBody(), 200).get("id").asText(); }
  private JsonNode confirm(String id) throws Exception { return write(API + "/" + id + "/confirm", owner, Map.of(), 200); }
  private JsonNode refresh(String id) throws Exception { return write(API + "/" + id + "/refresh", owner, Map.of(), 200); }
  private JsonNode read(String url, Session session) throws Exception {
    return json.readTree(mvc.perform(get(url).header("Authorization", session.authorization())).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString());
  }
  private JsonNode write(String url, Session session, Map<String, ?> body, int expected) throws Exception {
    return json.readTree(mvc.perform(post(url).header("Authorization", session.authorization())
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
  }
  private Session register() throws Exception {
    var response = json.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("fullName", "Synthetic Sandbox Customer", "email",
            "external-fixture-" + UUID.randomUUID() + "@example.test", "password", "SyntheticSandbox@Test2026!"))))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    return new Session(response.get("user").get("id").asText(), "Bearer " + response.get("accessToken").asText());
  }
  private void noMoneyMoved() {
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", BigDecimal.class, source)).isEqualByComparingTo("2000");
    assertThat(db.queryForObject("SELECT SUM(balance) FROM accounts WHERE account_category='SYSTEM'", BigDecimal.class)).isZero();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM journal_entries", Long.class)).isZero();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM ledger_entries", Long.class)).isZero();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM transactions", Long.class)).isEqualTo(1);
    assertThat(db.queryForObject("SELECT status FROM transactions WHERE id='fixture-unrelated-bill'", String.class)).isEqualTo("UPCOMING");
  }
  private record Session(String id, String authorization) {}
  private static Connection initialize() {
    try { return AccountApplicationTestDatabase.initializedExternalTransferDatabase(); }
    catch (Exception error) { throw new ExceptionInInitializerError(error); }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FakeConfiguration {
    @Bean @Primary FakeProvider fakeExternalProvider(JdbcTemplate db) { return new FakeProvider(db); }
  }
  static class FakeProvider implements ExternalPayoutProvider {
    final JdbcTemplate db;
    final AtomicInteger submissions = new AtomicInteger();
    final AtomicInteger lookups = new AtomicInteger();
    final Map<String, PayoutRequest> requests = new ConcurrentHashMap<>();
    volatile Environment environment;
    volatile boolean available;
    volatile Function<PayoutRequest, Outcome> submission;
    volatile Function<String, Outcome> lookup;
    volatile CountDownLatch entered;
    volatile CountDownLatch release;
    FakeProvider(JdbcTemplate db) { this.db = db; reset(); }
    void reset() {
      environment = Environment.SANDBOX; available = true;
      submissions.set(0); lookups.set(0); requests.clear();
      entered = null; release = null;
      submission = FakeProvider::success; lookup = id -> success(requests.get(id));
    }
    @Override public Environment environment() { return environment; }
    @Override public boolean available() { return available; }
    @Override public String unavailableReason() { return "Sandbox provider is disabled or missing fixture credentials."; }
    @Override public Outcome submit(PayoutRequest request) {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      assertThat(db.queryForObject("SELECT status FROM external_transfer_reviews WHERE provider_request_id=?",
          String.class, request.transferId())).isEqualTo("SUBMITTING");
      assertThat(request.transferId()).matches("[A-Za-z0-9_]{1,40}");
      submissions.incrementAndGet(); requests.put(request.transferId(), request);
      if (entered != null) entered.countDown();
      if (release != null) {
        try { assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
      }
      return submission.apply(request);
    }
    @Override public Outcome status(String transferId) {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      lookups.incrementAndGet(); return lookup.apply(transferId);
    }
    static Outcome success(PayoutRequest request) {
      return new Outcome(State.SUCCESS, "CF123", "SUCCESS", "COMPLETED", "UTR123", "Safe fixture result",
          request.transferId(), request.amount(), request.bankAccountNumber(), request.ifsc());
    }
  }
}
