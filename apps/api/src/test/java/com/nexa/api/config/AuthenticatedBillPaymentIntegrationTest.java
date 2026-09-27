package com.nexa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexa.api.NexaApiApplication;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Real authentication, HTTP, SQL, JPA and ledger; every balance belongs to an isolated H2 fixture. */
@SpringBootTest(classes = NexaApiApplication.class, properties = {
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.default_schema=PUBLIC", "spring.jpa.open-in-view=false",
    "spring.jpa.show-sql=false", "logging.level.root=WARN",
    "spring.datasource.hikari.schema=PUBLIC", "spring.datasource.hikari.connection-init-sql=SELECT 1",
    "spring.sql.init.mode=never", "spring.config.import=", "nexa.onboarding.enabled=false",
    "nexa.onboarding.documents.enabled=false", "nexa.onboarding.documents.root=",
    "nexa.onboarding.identity.key-id=", "nexa.onboarding.identity.encryption-key=",
    "nexa.admin.bootstrap.email=", "nexa.admin.bootstrap.password=",
    "nexa.security.jwt.secret=isolated-http-bill-payment-secret-with-more-than-32-bytes",
    "nexa.security.jwt.issuer=isolated-http-bill-payment", "nexa.ai.enabled=false",
    "nexa.cors.allowed-origins=http://localhost:8000"
})
@ActiveProfiles("integration")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthenticatedBillPaymentIntegrationTest {
  private static final String API = "/api/v1/bill-payments";
  private static final Connection KEEP_ALIVE = initialize();

  @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> {
      try { return KEEP_ALIVE.getMetaData().getURL() + ";MODE=Oracle;LOCK_TIMEOUT=10000"; }
      catch (Exception error) { throw new IllegalStateException(error); }
    });
    properties.add("spring.datasource.username", () -> "sa");
    properties.add("spring.datasource.password", () -> "");
    properties.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired com.nexa.api.security.JwtService jwt;
  @Autowired com.nexa.api.service.BillPaymentService billPayments;
  private Session owner;
  private Session recipient;
  private long source;
  private long destination;
  private String payee;
  private String bill;

  @BeforeEach void setup() throws Exception {
    try (Connection connection = Objects.requireNonNull(db.getDataSource()).getConnection()) {
      assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:account-applications-");
    }
    for (String table : List.of("bill_payment_attempts", "ledger_entries", "journal_entries",
        "transactions", "customer_credentials")) db.update("DELETE FROM " + table);
    db.update("DELETE FROM accounts WHERE account_category='CUSTOMER'");
    db.update("DELETE FROM customers");
    owner = register("Bill customer");
    recipient = register("Actual recipient");
    source = account(owner, "900000100001", "Fixture funding", "2000.00");
    destination = account(recipient, "900000200001", "Fixture biller", "0.00");
    payee = write("/api/v1/beneficiaries", owner,
        Map.of("displayName", "Electricity payee nickname", "accountNumber", "900000200001"), 200).get("id").asText();
    bill = newBill("1000.00", "100.00");
  }

  @AfterAll static void closeKeeper() throws Exception { KEEP_ALIVE.close(); }

  @Test void fullPaymentPostsExactlyOnceAndKeepsBillAndRecipientMetadata() throws Exception {
    var body = prepareBody(null, payee);
    var review = write(API + "/prepare", owner, body, 200);
    String id = review.get("id").asText();
    assertThat(id).isEqualTo("BP-" + body.get("requestKey"));
    assertThat(review.get("recipientName").asText()).isEqualTo("Synthetic Actual recipient");
    assertThat(review.get("destinationMasked").asText()).isEqualTo("•••• 0001");
    assertThat(review.get("amount").asText()).isEqualTo("1000.00");
    assertThat(review.get("status").asText()).isEqualTo("READY");
    assertThat(read(API + "/" + id, owner).get("id").asText()).isEqualTo(id);
    assertThat(write(API + "/prepare", owner, body, 200).get("id").asText()).isEqualTo(id);
    noPosting();
    assertThat(db.queryForObject("SELECT target_id FROM transactions WHERE id=?", String.class, bill)).isEqualTo(payee);
    var completed = confirm(id);
    assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(completed.get("reference").asText()).startsWith("TX-");
    assertThat(completed.get("completedAt").asText()).isNotBlank();
    assertThat(confirm(id).get("reference").asText()).isEqualTo(completed.get("reference").asText());
    assertThat(write(API + "/prepare", owner, body, 200).get("status").asText()).isEqualTo("COMPLETED");
    assertThat(write(API + "/" + id + "/cancel", owner, Map.of(), 200).get("status").asText()).isEqualTo("COMPLETED");
    assertThat(balance(source)).isEqualByComparingTo("1000");
    assertThat(balance(destination)).isEqualByComparingTo("1000");
    assertThat(postings()).isEqualTo(1);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM journal_entries WHERE status='POSTED'", Long.class)).isEqualTo(1);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM ledger_entries", Long.class)).isEqualTo(2);
    assertBalanced();
    var row = db.queryForMap("SELECT * FROM transactions WHERE id=?", completed.get("reference").asText());
    assertThat(row.get("RECORD_KIND")).isEqualTo("PAYMENT");
    assertThat(row.get("OPERATION")).isEqualTo("BILL_PAYMENT");
    assertThat(row.get("TARGET_ID")).isEqualTo(bill);
    assertThat(row.get("PARENT_ID")).isEqualTo(bill);
    assertThat(row.get("MERCHANT_NAME")).isEqualTo("Fixture electricity");
    assertThat(row.get("CATEGORY")).isEqualTo("Utilities");
    assertThat(row.get("PAYMENT_METHOD")).isEqualTo("INTERNAL_TRANSFER");
    assertThat(row.get("USER_ID")).isEqualTo(owner.id());
    assertThat(db.queryForObject("SELECT amount FROM transactions WHERE id=?", BigDecimal.class, bill)).isEqualByComparingTo("1000");
    assertThat(read("/api/v1/bills/" + bill, owner).get("status").asText()).isEqualTo("PAID");
    assertThat(db.queryForObject("SELECT status FROM transactions WHERE id=?", String.class, bill)).isNotEqualTo("PAID");
    assertThat(read(API + "?billId=" + bill, owner).size()).isEqualTo(1);
    write(API + "/prepare", owner, prepareBody(null, null), 400);
  }

  @Test void partialThenFinalPaymentUsesRemainingAndAllowsLastAmountBelowMinimum() throws Exception {
    write(API + "/prepare", owner, prepareBody("99.99", payee), 400);
    write(API + "/prepare", owner, prepareBody("1000.01", payee), 400);
    var first = prepare("850.00", payee);
    assertThat(confirm(first).get("status").asText()).isEqualTo("COMPLETED");
    assertThat(db.queryForObject("SELECT status FROM transactions WHERE id=?", String.class, bill)).isNotEqualTo("PAID");
    String otherPayee = anotherPayee();
    write(API + "/prepare", owner, prepareBody("150.00", otherPayee), 400);
    assertThat(confirm(prepare("100.00", null)).get("status").asText()).isEqualTo("COMPLETED");
    var last = write(API + "/prepare", owner, prepareBody(null, null), 200);
    assertThat(last.get("amount").asText()).isEqualTo("50.00");
    assertThat(confirm(last.get("id").asText()).get("status").asText()).isEqualTo("COMPLETED");
    assertThat(balance(source)).isEqualByComparingTo("1000");
    assertThat(balance(destination)).isEqualByComparingTo("1000");
    assertThat(postings()).isEqualTo(3);
    assertBalanced();
  }

  @Test void extremeAmountExponentsAreRejectedBeforeRequestFingerprintFormatting() throws Exception {
    for (String amount : List.of("10000000000000", "1E+2147483647", "1E-2147483647")) {
      write(API + "/prepare", owner, prepareBody(amount, payee), 400);
      // Internal chat callers do not pass through controller bean validation.
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> billPayments.prepare(
          new com.nexa.api.service.BillPaymentService.PrepareRequest(UUID.randomUUID(), bill,
              Long.toString(source), payee, new BigDecimal(amount))))
          .isInstanceOf(com.nexa.api.exep.InvalidRequestException.class);
    }
    assertThat(db.queryForObject("SELECT COUNT(*) FROM bill_payment_attempts", Long.class)).isZero();
    assertThat(db.queryForObject("SELECT target_id FROM transactions WHERE id=?", String.class, bill)).isNull();
    noPosting();
  }

  @Test void oldReviewCannotPayWhenAnEarlierPartialPaymentChangesOutstanding() throws Exception {
    String first = prepare("200", payee);
    String old = prepare("300", null);
    confirm(first);
    var failure = confirm(old);
    assertThat(failure.get("status").asText()).isEqualTo("FAILED");
    assertThat(failure.get("failureReason").asText()).contains("outstanding amount changed");
    assertThat(confirm(old).get("status").asText()).isEqualTo("FAILED");
    assertThat(postings()).isEqualTo(1);
    assertThat(confirm(prepare(null, null)).get("status").asText()).isEqualTo("COMPLETED");
    assertThat(balance(destination)).isEqualByComparingTo("1000");
  }

  @Test void completedPaymentKeepsRecipientFixedEvenIfItsJournalIsLaterReversed() throws Exception {
    String completed = confirm(prepare("200", payee)).get("reference").asText();
    db.update("UPDATE journal_entries SET status='REVERSED' WHERE transaction_id=?", completed);
    String replacement = anotherPayee();
    var error = write(API + "/prepare", owner, prepareBody("200", replacement), 400);
    assertThat(error.get("detail").asText()).contains("recipient cannot be changed");
    assertThat(db.queryForObject("SELECT target_id FROM transactions WHERE id=?", String.class, bill)).isEqualTo(payee);
    assertThat(postings()).isEqualTo(1);
  }

  @Test void parallelConfirmationOfOneReviewPostsOnce() throws Exception {
    String id = prepare(null, payee);
    var results = concurrentConfirm(id, id);
    assertThat(results).allSatisfy(r -> assertThat(r.get("status").asText()).isEqualTo("COMPLETED"));
    assertThat(results.get(0).get("reference").asText()).isEqualTo(results.get(1).get("reference").asText());
    assertThat(postings()).isEqualTo(1);
    assertThat(balance(source)).isEqualByComparingTo("1000");
    assertBalanced();
  }

  @Test void parallelFullReviewsOfOneBillCannotBothDebit() throws Exception {
    String first = prepare(null, payee);
    String second = prepare(null, null);
    var results = concurrentConfirm(first, second);
    assertThat(results.stream().map(r -> r.get("status").asText()).toList()).containsExactlyInAnyOrder("COMPLETED", "FAILED");
    assertThat(postings()).isEqualTo(1);
    assertThat(balance(destination)).isEqualByComparingTo("1000");
    assertBalanced();
  }

  @Test void cancellationAndExpiryPersistAndNeverPost() throws Exception {
    String cancelled = prepare(null, payee);
    assertThat(write(API + "/" + cancelled + "/cancel", owner, Map.of(), 200).get("status").asText()).isEqualTo("CANCELLED");
    assertThat(confirm(cancelled).get("status").asText()).isEqualTo("CANCELLED");
    String expired = prepare(null, null);
    db.update("UPDATE bill_payment_attempts SET expires_at=? WHERE id=?", Timestamp.from(Instant.now().minusSeconds(10)), expired);
    assertThat(confirm(expired).get("status").asText()).isEqualTo("EXPIRED");
    assertThat(db.queryForObject("SELECT status FROM bill_payment_attempts WHERE id=?", String.class, expired)).isEqualTo("EXPIRED");
    assertThat(read(API + "?billId=" + bill, owner).size()).isEqualTo(2);
    noPosting();
  }

  @Test void insufficientFundsAreDurableFailureAndRequireANewReviewAfterFunding() throws Exception {
    String id = prepare(null, payee);
    db.update("UPDATE accounts SET balance=10 WHERE id=?", source);
    var result = confirm(id);
    assertThat(result.get("status").asText()).isEqualTo("FAILED");
    assertThat(result.get("failureReason").asText()).contains("not enough money");
    db.update("UPDATE accounts SET balance=2000 WHERE id=?", source);
    assertThat(confirm(id).get("status").asText()).isEqualTo("FAILED");
    noPosting();
    assertThat(confirm(prepare(null, null)).get("status").asText()).isEqualTo("COMPLETED");
  }

  @Test void inactiveAccountsAndPayeeChangesAreRecheckedAtConfirmation() throws Exception {
    String blocked = prepare(null, payee);
    db.update("UPDATE accounts SET status='BLOCKED' WHERE id=?", destination);
    assertThat(confirm(blocked).get("status").asText()).isEqualTo("FAILED");
    db.update("UPDATE accounts SET status='ACTIVE' WHERE id=?", destination);
    String paused = prepare(null, null);
    db.update("UPDATE transactions SET status='INACTIVE' WHERE id=?", payee);
    assertThat(confirm(paused).get("status").asText()).isEqualTo("FAILED");
    db.update("UPDATE transactions SET status='ACTIVE' WHERE id=?", payee);
    String changed = prepare(null, null);
    account(recipient, "900000200002", "Alternate actual recipient", "0");
    mvc.perform(put("/api/v1/beneficiaries/" + payee).header("Authorization", owner.authorization())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "displayName", "Same nickname", "accountNumber", "900000200002"))))
        .andExpect(status().isOk());
    assertThat(confirm(changed).get("failureReason").asText()).contains("saved payee's account changed");
    noPosting();
  }

  @Test void choosingDifferentPayeeInvalidatesOldReviewAndPersistsNewRoute() throws Exception {
    String old = prepare(null, payee);
    String replacement = anotherPayee();
    String current = prepare(null, replacement);
    assertThat(confirm(old).get("status").asText()).isEqualTo("FAILED");
    assertThat(read(API + "/" + current, owner).get("payeeId").asText()).isEqualTo(replacement);
    assertThat(db.queryForObject("SELECT target_id FROM transactions WHERE id=?", String.class, bill)).isEqualTo(replacement);
    assertThat(confirm(current).get("status").asText()).isEqualTo("COMPLETED");
    assertThat(balance(destination)).isZero();
    assertThat(postings()).isEqualTo(1);
  }

  @Test void authenticationOwnershipAndVerifiedCustomerRoutesAreRequired() throws Exception {
    mvc.perform(post(API + "/prepare").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(prepareBody(null, payee)))).andExpect(status().isUnauthorized());
    write(API + "/prepare", recipient, prepareBody(null, payee), 404);
    var foreignSource = prepareBody(null, payee);
    foreignSource.put("sourceAccountId", Long.toString(destination));
    write(API + "/prepare", owner, foreignSource, 400);
    String id = prepare(null, payee);
    mvc.perform(get(API + "/" + id).header("Authorization", recipient.authorization())).andExpect(status().isNotFound());
    write(API + "/" + id + "/confirm", recipient, Map.of(), 404);
    write(API + "/" + id + "/cancel", recipient, Map.of(), 404);
    mvc.perform(get(API + "?billId=" + bill).header("Authorization", recipient.authorization())).andExpect(status().isNotFound());
    var changed = prepareBody(null, payee);
    changed.put("requestKey", id.substring(3));
    changed.put("amount", "200.00");
    write(API + "/prepare", owner, changed, 409);
    db.update("UPDATE transactions SET destination_hash=NULL WHERE id=?", payee);
    assertThat(confirm(id).get("status").asText()).isEqualTo("FAILED");
    noPosting();
  }

  @Test void aSignedTokenWithOwnerIdentityButWrongRoleCannotUseAnyBillPaymentEndpoint() throws Exception {
    String id = prepare(null, payee);
    // Use the real signing/decoding pipeline. Ownership alone must never grant payment access.
    String email = db.queryForObject("SELECT email FROM customers WHERE user_id=?", String.class, owner.id());
    Session viewer = new Session(owner.id(), "Bearer " + jwt.issue(owner.id(), email, "VIEWER").value());
    write(API + "/prepare", viewer, prepareBody(null, payee), 403);
    mvc.perform(get(API + "/" + id).header("Authorization", viewer.authorization())).andExpect(status().isForbidden());
    write(API + "/" + id + "/confirm", viewer, Map.of(), 403);
    write(API + "/" + id + "/cancel", viewer, Map.of(), 403);
    mvc.perform(get(API + "?billId=" + bill).header("Authorization", viewer.authorization())).andExpect(status().isForbidden());
    assertThat(read(API + "/" + id, owner).get("status").asText()).isEqualTo("READY");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM bill_payment_attempts", Long.class)).isEqualTo(1);
    noPosting();
  }

  @Test void missingUnlinkedAndSystemPayeesAndLegacyPaidBillsCannotDebit() throws Exception {
    write(API + "/prepare", owner, prepareBody(null, null), 400);
    db.update("UPDATE transactions SET destination_account_id=NULL WHERE id=?", payee);
    write(API + "/prepare", owner, prepareBody(null, payee), 400);
    long system = db.queryForObject("SELECT id FROM accounts WHERE account_number='SYSTEM-CASH'", Long.class);
    String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
        .digest(("NEXA:" + system).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    db.update("UPDATE transactions SET destination_account_id=?,destination_hash=? WHERE id=?", system, hash, payee);
    write(API + "/prepare", owner, prepareBody(null, payee), 400);
    db.update("UPDATE transactions SET status='PAID' WHERE id=?", bill);
    write(API + "/prepare", owner, prepareBody(null, payee), 400);
    noPosting();
    assertThat(db.queryForObject("SELECT SUM(balance) FROM accounts WHERE account_category='SYSTEM'", BigDecimal.class)).isZero();
  }

  @Test void unexpectedLatePersistenceFailureRollsBackMoneyAndLeavesReviewRetryable() throws Exception {
    String id = prepare(null, payee);
    // This fails after TransactionService has flushed transaction, balances and both ledger entries.
    db.execute("ALTER TABLE bill_payment_attempts ADD CONSTRAINT h2_reject_bill_completion CHECK(status<>'COMPLETED')");
    try {
      mvc.perform(post(API + "/" + id + "/confirm").header("Authorization", owner.authorization())
          .contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().is5xxServerError());
      assertThat(read(API + "/" + id, owner).get("status").asText()).isEqualTo("READY");
      noPosting();
      assertThat(db.queryForObject("SELECT status FROM transactions WHERE id=?", String.class, bill)).isNotEqualTo("PAID");
    } finally { db.execute("ALTER TABLE bill_payment_attempts DROP CONSTRAINT h2_reject_bill_completion"); }
    assertThat(confirm(id).get("status").asText()).isEqualTo("COMPLETED");
    assertThat(postings()).isEqualTo(1);
    assertBalanced();
  }

  private List<JsonNode> concurrentConfirm(String first, String second) throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try {
      var futures = new ArrayList<java.util.concurrent.Future<JsonNode>>();
      for (String id : List.of(first, second)) futures.add(pool.submit(() -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture start timeout");
        return confirm(id);
      }));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      var results = new ArrayList<JsonNode>();
      for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
      return results;
    } finally { start.countDown(); pool.shutdownNow(); }
  }

  private String anotherPayee() throws Exception {
    account(recipient, "900000200003", "Alternative biller", "0");
    return write("/api/v1/beneficiaries", owner, Map.of("displayName", "Other payee", "accountNumber", "900000200003"), 200).get("id").asText();
  }
  private String newBill(String amount, String minimum) throws Exception {
    return write("/api/v1/bills", owner, Map.of("billerName", "Fixture electricity", "amount", amount,
        "minimumAmount", minimum, "dueAt", LocalDate.now().plusDays(2).toString(),
        "category", "Utilities", "customerNumber", "Fixture reference"), 200).get("id").asText();
  }
  private Map<String, Object> prepareBody(String amount, String selectedPayee) {
    var body = new LinkedHashMap<String, Object>();
    body.put("requestKey", UUID.randomUUID().toString()); body.put("billId", bill);
    body.put("sourceAccountId", Long.toString(source));
    if (selectedPayee != null) body.put("payeeId", selectedPayee);
    if (amount != null) body.put("amount", amount);
    return body;
  }
  private String prepare(String amount, String selectedPayee) throws Exception {
    return write(API + "/prepare", owner, prepareBody(amount, selectedPayee), 200).get("id").asText();
  }
  private JsonNode confirm(String id) throws Exception { return write(API + "/" + id + "/confirm", owner, Map.of(), 200); }
  private JsonNode read(String url, Session session) throws Exception {
    return json.readTree(mvc.perform(get(url).header("Authorization", session.authorization()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }
  private JsonNode write(String url, Session session, Map<String, ?> body, int expected) throws Exception {
    return json.readTree(mvc.perform(post(url).header("Authorization", session.authorization())
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
  }
  private Session register(String name) throws Exception {
    JsonNode response = json.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("fullName", "Synthetic " + name,
            "email", "http-bills-" + UUID.randomUUID() + "@example.test", "password", "SyntheticBill@Test2026!"))))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    return new Session(response.get("user").get("id").asText(), "Bearer " + response.get("accessToken").asText());
  }
  private long account(Session customer, String number, String name, String balance) {
    long customerId = db.queryForObject("SELECT id FROM customers WHERE user_id=?", Long.class, customer.id());
    db.update("INSERT INTO accounts(account_number,customer_id,account_name,account_type,account_category,currency_code,"
            + "balance,status,created_at,updated_at) VALUES(?,?,?,'SAVINGS','CUSTOMER','INR',?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        number, customerId, name, new BigDecimal(balance));
    return db.queryForObject("SELECT id FROM accounts WHERE account_number=?", Long.class, number);
  }
  private BigDecimal balance(long account) { return db.queryForObject("SELECT balance FROM accounts WHERE id=?", BigDecimal.class, account); }
  private long postings() { return db.queryForObject("SELECT COUNT(*) FROM transactions WHERE record_kind='PAYMENT'", Long.class); }
  private void noPosting() {
    assertThat(postings()).isZero();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM journal_entries", Long.class)).isZero();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM ledger_entries", Long.class)).isZero();
    assertThat(balance(source)).isEqualByComparingTo("2000");
    assertThat(balance(destination)).isZero();
  }
  private void assertBalanced() {
    for (BigDecimal delta : db.queryForList("SELECT SUM(CASE WHEN entry_type='DEBIT' THEN amount ELSE -amount END)"
        + " FROM ledger_entries GROUP BY journal_entry_id", BigDecimal.class)) assertThat(delta).isZero();
  }
  private record Session(String id, String authorization) {}
  private static Connection initialize() {
    try { return AccountApplicationTestDatabase.initializedBillPaymentDatabase(); }
    catch (Exception error) { throw new ExceptionInInitializerError(error); }
  }
}
