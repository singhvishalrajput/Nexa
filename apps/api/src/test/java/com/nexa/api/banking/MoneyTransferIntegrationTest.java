package com.nexa.api.banking;


import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.*;

@SpringBootTest(
    properties = {
      "spring.profiles.active=integration",
      "spring.datasource.url=jdbc:h2:mem:moneytransfers;MODE=Oracle;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.show-sql=false",
      "nexa.security.jwt.secret=money-transfer-test-secret-long-enough-for-jwt",
      "nexa.cors.allowed-origins=http://localhost:8000"
    })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoneyTransferIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  String auth, otherAuth, source, ownDestination, destination, number;

  @BeforeAll
  void schema() {}

  @BeforeEach
  void fixture() throws Exception {
    auth = register("Sender");
    source = open(auth, "Everyday");
    ownDestination = open(auth, "Reserve");
    otherAuth = register("Recipient");
    destination = open(otherAuth, "Recipient savings");
    number =
        db.queryForObject(
            "SELECT account_number FROM accounts WHERE id=?", String.class, destination);
    db.update("UPDATE accounts SET balance=1000 WHERE id=?", source);
  }

  String register(String name) throws Exception {
    return "Bearer "
        + postJson(
                "/api/v1/auth/register",
                Map.of(
                    "fullName",
                    name,
                    "email",
                    UUID.randomUUID() + "@example.com",
                    "password",
                    "SecureTest@123"),
                null,
                201)
            .get("accessToken")
            .asText();
  }

  String open(String token, String name) throws Exception {
    return postJson(
            "/api/v1/accounts",
            Map.of(
                "displayName",
                name,
                "accountType",
                "SAVINGS",
                "currencyCode",
                "INR",
                "dateOfBirth",
                "1990-01-01",
                "address",
                "Mumbai"),
            token,
            201)
        .get("id")
        .asText();
  }

  JsonNode postJson(String path, Object body, String token, int expected) throws Exception {
    var request =
        post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    if (token != null) request.header("Authorization", token);
    return json.readTree(
        mvc.perform(request)
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  Map<String, Object> draft(String amount) {
    return Map.of("sourceAccountId", source, "destinationAccountNumber", number, "amount", amount);
  }

  JsonNode prepare(String amount) throws Exception {
    return postJson("/api/v1/money-transfers/prepare", draft(amount), auth, 200);
  }

  JsonNode confirm(String id, int expected) throws Exception {
    return postJson("/api/v1/money-transfers/" + id + "/confirm", Map.of(), auth, expected);
  }

  BigDecimal balance(String id) {
    return db.queryForObject("SELECT balance FROM accounts WHERE id=?", BigDecimal.class, id);
  }

  @Test
  void reviewDoesNotMoveMoneyAndConfirmationPostsBalancedLedgerOnce() throws Exception {
    var review = prepare("125.25");
    String id = review.get("id").asText();
    assertThat(review.get("recipientName").asText()).isEqualTo("Recipient");
    assertThat(review.toString()).doesNotContain(number);
    assertThat(balance(source)).isEqualByComparingTo("1000");
    var receipt = confirm(id, 200);
    String ref = receipt.get("reference").asText();
    assertThat(receipt.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(balance(source)).isEqualByComparingTo("874.75");
    assertThat(balance(destination)).isEqualByComparingTo("125.25");
    assertThat(confirm(id, 200)).isEqualTo(receipt);
    mvc.perform(get("/api/v1/transactions/" + ref).header("Authorization", auth))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/transactions/" + ref).header("Authorization", otherAuth))
        .andExpect(status().isOk());
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM journal_entries WHERE transaction_id=?", Integer.class, ref))
        .isEqualTo(1);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries l JOIN journal_entries j ON"
                    + " j.id=l.journal_entry_id WHERE j.transaction_id=?",
                Integer.class,
                ref))
        .isEqualTo(2);
    assertThat(
            db.queryForObject(
                "SELECT SUM(CASE WHEN l.entry_type='DEBIT' THEN l.amount ELSE -l.amount END) FROM"
                    + " ledger_entries l JOIN journal_entries j ON j.id=l.journal_entry_id WHERE"
                    + " j.transaction_id=?",
                BigDecimal.class,
                ref))
        .isEqualByComparingTo("0");
  }

  @Test
  void concurrentConfirmationsReturnOneTransaction() throws Exception {
    String id = prepare("40").get("id").asText();
    var executor = Executors.newFixedThreadPool(2);
    try {
      var start = new CountDownLatch(1);
      Callable<JsonNode> call =
          () -> {
            start.await();
            return confirm(id, 200);
          };
      var first = executor.submit(call);
      var second = executor.submit(call);
      start.countDown();
      assertThat(first.get(20, TimeUnit.SECONDS).get("reference"))
          .isEqualTo(second.get(20, TimeUnit.SECONDS).get("reference"));
      assertThat(balance(source)).isEqualByComparingTo("960");
      assertThat(balance(destination)).isEqualByComparingTo("40");
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void sourceAndReviewOwnershipAreEnforced() throws Exception {
    postJson("/api/v1/money-transfers/prepare", draft("10"), otherAuth, 404);
    String id = prepare("10").get("id").asText();
    postJson("/api/v1/money-transfers/" + id + "/confirm", Map.of(), otherAuth, 404);
    mvc.perform(get("/api/v1/money-transfers/" + id).header("Authorization", otherAuth))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/money-transfers/" + id + "/confirm"))
        .andExpect(status().isUnauthorized());
    assertThat(balance(source)).isEqualByComparingTo("1000");
  }

  @Test
  void ownAccountTransfersWorkAndForeignIdsCannotBeUsedAsOwnAccounts() throws Exception {
    var review =
        postJson(
            "/api/v1/money-transfers/prepare",
            Map.of(
                "sourceAccountId", source, "destinationAccountId", ownDestination, "amount", "20"),
            auth,
            200);
    confirm(review.get("id").asText(), 200);
    assertThat(balance(ownDestination)).isEqualByComparingTo("20");
    postJson(
        "/api/v1/money-transfers/prepare",
        Map.of("sourceAccountId", source, "destinationAccountId", destination, "amount", "20"),
        auth,
        404);
  }

  @Test
  void invalidAmountsSameAccountAndMissingRecipientsCannotBePrepared() throws Exception {
    for (String amount : List.of("0", "-1", "0.001", "10000000000000", "1001"))
      postJson("/api/v1/money-transfers/prepare", draft(amount), auth, 400);
    postJson(
        "/api/v1/money-transfers/prepare",
        Map.of("sourceAccountId", source, "destinationAccountId", source, "amount", "10"),
        auth,
        400);
    postJson(
        "/api/v1/money-transfers/prepare",
        Map.of(
            "sourceAccountId",
            source,
            "destinationAccountNumber",
            "999999999999999999999999999999",
            "amount",
            "10"),
        auth,
        400);
    var ambiguous = new HashMap<>(draft("10"));
    ambiguous.put("destinationAccountId", ownDestination);
    postJson("/api/v1/money-transfers/prepare", ambiguous, auth, 400);
  }

  @Test
  void balanceStatusAndExpiryAreRecheckedAtConfirmation() throws Exception {
    String id = prepare("100").get("id").asText();
    db.update("UPDATE accounts SET balance=50 WHERE id=?", source);
    confirm(id, 400);
    assertThat(balance(destination)).isEqualByComparingTo("0");
    db.update("UPDATE accounts SET balance=1000 WHERE id=?", source);
    db.update("UPDATE accounts SET status='BLOCKED' WHERE id=?", destination);
    confirm(id, 400);
    db.update("UPDATE accounts SET status='ACTIVE' WHERE id=?", destination);
    db.update("UPDATE transactions SET expires_at=TIMESTAMP '2000-01-01 00:00:00' WHERE id=?", id);
    confirm(id, 400);
    mvc.perform(get("/api/v1/money-transfers/" + id).header("Authorization", auth))
        .andExpect(jsonPath("$.status").value("EXPIRED"));
    assertThat(balance(source)).isEqualByComparingTo("1000");
  }

  @Test
  void systemAndDifferentCurrencyAccountsAreRejected() throws Exception {
    db.update("UPDATE accounts SET currency_code='USD' WHERE id=?", destination);
    postJson("/api/v1/money-transfers/prepare", draft("10"), auth, 400);
    db.update(
        "UPDATE accounts SET currency_code='INR',account_category='SYSTEM' WHERE id=?",
        destination);
    postJson("/api/v1/money-transfers/prepare", draft("10"), auth, 400);
  }

  @Test
  void ledgerFailureRollsBackBalancesTransactionAndReceipt() throws Exception {
    String id = prepare("73.21").get("id").asText();
    db.execute(
        "ALTER TABLE ledger_entries ADD CONSTRAINT test_ledger_failure CHECK (amount <> 73.21)");
    try {
      confirm(id, 503);
    } finally {
      db.execute("ALTER TABLE ledger_entries DROP CONSTRAINT test_ledger_failure");
    }
    assertThat(balance(source)).isEqualByComparingTo("1000");
    assertThat(balance(destination)).isEqualByComparingTo("0");
    assertThat(db.queryForObject("SELECT status FROM transactions WHERE id=?", String.class, id))
        .isEqualTo("READY");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE record_kind='PAYMENT' AND"
                    + " source_account_id=?",
                Integer.class,
                source))
        .isZero();
    confirm(id, 200);
  }

  @Test
  void differentRequestsCannotOverdrawTheSameAccount() throws Exception {
    String firstId = prepare("700").get("id").asText();
    String secondId = prepare("700").get("id").asText();
    var executor = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      var results = new ArrayList<Future<Integer>>();
      for (String id : List.of(firstId, secondId))
        results.add(
            executor.submit(
                () -> {
                  start.await();
                  return mvc.perform(
                          post("/api/v1/money-transfers/" + id + "/confirm")
                              .header("Authorization", auth))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      start.countDown();
      assertThat(
              List.of(
                  results.get(0).get(20, TimeUnit.SECONDS),
                  results.get(1).get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 400);
      assertThat(balance(source)).isEqualByComparingTo("300");
      assertThat(balance(destination)).isEqualByComparingTo("700");
    } finally {
      executor.shutdownNow();
    }
  }
}
