package com.nexa.api.banking;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.*;

/** Runs unchanged against H2 or the explicitly configured, migrated Oracle schema. */
@SpringBootTest(
    properties = {
      "spring.profiles.active=integration",
      "spring.flyway.enabled=false",
      "spring.jpa.show-sql=false",
      "nexa.security.jwt.secret=six-table-test-secret-with-at-least-32-bytes",
      "nexa.cors.allowed-origins=http://localhost:8000"
    })
@AutoConfigureMockMvc
class SixTableBankingIntegrationTest {
  static final boolean ORACLE = "true".equals(System.getenv("NEXA_VERIFY_ORACLE"));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    if (ORACLE)
      r.add(
          "spring.datasource.hikari.connection-init-sql",
          () -> "ALTER SESSION SET TIME_ZONE='UTC'");
    r.add(
        "spring.datasource.url",
        () ->
            ORACLE
                ? System.getenv("BANKING_DB_URL")
                : "jdbc:h2:mem:sixbank;MODE=Oracle;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> ORACLE ? System.getenv("BANKING_DB_USERNAME") : "sa");
    r.add("spring.datasource.password", () -> ORACLE ? System.getenv("BANKING_DB_PASSWORD") : "");
    r.add(
        "spring.datasource.driver-class-name",
        () -> ORACLE ? "oracle.jdbc.OracleDriver" : "org.h2.Driver");
    r.add(
        "spring.datasource.hikari.schema",
        () -> ORACLE ? System.getenv("BANKING_DB_SCHEMA") : "PUBLIC");
    r.add("spring.jpa.hibernate.ddl-auto", () -> ORACLE ? "validate" : "create-drop");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  String auth, otherAuth, source, destination, email;

  JsonNode postJson(String path, Object data, String token, int status) throws Exception {
    var q =
        post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data));
    if (token != null) q.header("Authorization", token);
    return json.readTree(
        mvc.perform(q)
            .andExpect(status().is(status))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  JsonNode getJson(String path, String token) throws Exception {
    return json.readTree(
        mvc.perform(get(path).header("Authorization", token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  String register(String email) throws Exception {
    return "Bearer "
        + postJson(
                "/api/v1/auth/register",
                Map.of(
                    "email",
                    email,
                    "fullName",
                    "Six table validation",
                    "password",
                    "SixTable@Test123"),
                null,
                201)
            .get("accessToken")
            .asText();
  }

  String open(String token) throws Exception {
    return postJson(
            "/api/v1/accounts",
            Map.of(
                "displayName",
                "Validation savings",
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

  BigDecimal balance(String id) {
    return db.queryForObject("SELECT balance FROM accounts WHERE id=?", BigDecimal.class, id);
  }

  @BeforeEach
  void setup() throws Exception {
    email = "six-" + UUID.randomUUID() + "@example.com";
    auth = register(email);
    source = open(auth);
    otherAuth = register("six-" + UUID.randomUUID() + "@example.com");
    destination = open(otherAuth);
    if (db.queryForObject(
            "SELECT COUNT(*) FROM accounts WHERE account_type='CASH' AND account_category='SYSTEM'",
            Integer.class)
        == 0)
      db.update(
          "INSERT INTO"
              + " accounts(account_number,account_name,account_type,account_category,balance,currency_code,status,version,created_at,updated_at)"
              + " VALUES('SIX-CASH','Cash','CASH','SYSTEM',0,'INR','ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    mvc.perform(
            post("/api/transactions/deposit")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"destinationAccountId\":" + source + ",\"amount\":1000}"))
        .andExpect(status().isCreated());
  }

  @Test
  void customerLoginTransferHistoryAndRefreshSurviveConsolidation() throws Exception {
    var login =
        postJson(
            "/api/v1/auth/login",
            Map.of("email", email, "password", "SixTable@Test123"),
            null,
            200);
    assertThat(login.get("accessToken").asText()).isNotBlank();
    postJson(
        "/api/v1/auth/refresh",
        Map.of("refreshToken", login.get("refreshToken").asText()),
        null,
        200);
    postJson(
        "/api/v1/auth/refresh",
        Map.of("refreshToken", login.get("refreshToken").asText()),
        null,
        401);
    assertThat(getJson("/api/v1/accounts", auth).size()).isEqualTo(1);
    assertThat(
            getJson("/api/v1/accounts/" + source + "/balance", auth)
                .get("availableBalance")
                .decimalValue())
        .isEqualByComparingTo("1000");
    String number =
        db.queryForObject(
            "SELECT account_number FROM accounts WHERE id=?", String.class, destination);
    var review =
        postJson(
            "/api/v1/money-transfers/prepare",
            Map.of("sourceAccountId", source, "destinationAccountNumber", number, "amount", "25"),
            auth,
            200);
    var receipt =
        postJson(
            "/api/v1/money-transfers/" + review.get("id").asText() + "/confirm",
            Map.of(),
            auth,
            200);
    assertThat(receipt.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(
            getJson("/api/v1/accounts/" + source + "/transactions", auth)
                .get("totalElements")
                .asLong())
        .isEqualTo(2);
    assertThat(balance(destination)).isEqualByComparingTo("25");
    balanced();
  }

  String mandate(String start, String end) throws Exception {
    var body = new HashMap<String, Object>();
    body.put("sourceAccountId", Long.valueOf(source));
    body.put("beneficiaryAccountId", Long.valueOf(destination));
    body.put("payee", "Authorized payee");
    body.put("limit", "100");
    body.put("startDate", start);
    if (end != null) body.put("endDate", end);
    return postJson("/api/v1/mandates", body, auth, 200).get("ID").asText();
  }

  Map<String, String> execution(String amount) {
    return Map.of("amount", amount, "requestId", UUID.randomUUID().toString());
  }

  @Test
  void mandateActivationExecutionRetryRevocationAndAudit() throws Exception {
    String id = mandate("2020-01-01", null);
    var request = execution("40");
    postJson("/api/v1/mandates/" + id + "/execute", request, auth, 400);
    postJson("/api/v1/mandates/" + id + "/activate", Map.of(), otherAuth, 404);
    postJson("/api/v1/mandates/" + id + "/activate", Map.of(), auth, 200);
    postJson("/api/v1/mandates/" + id + "/execute", execution("101"), auth, 400);
    var posted = postJson("/api/v1/mandates/" + id + "/execute", request, auth, 200);
    assertThat(postJson("/api/v1/mandates/" + id + "/execute", request, auth, 200))
        .isEqualTo(posted);
    assertThat(balance(source)).isEqualByComparingTo("960");
    assertThat(balance(destination)).isEqualByComparingTo("40");
    postJson("/api/v1/mandates/" + id + "/revoke", Map.of(), auth, 200);
    postJson("/api/v1/mandates/" + id + "/execute", execution("10"), auth, 400);
    postJson("/api/v1/mandates/" + id + "/activate", Map.of(), auth, 400);
    assertThat(getJson("/api/v1/mandates/" + id + "/history", auth).size()).isEqualTo(4);
    assertThat(getJson("/api/v1/mandates/" + id, auth).get("status").asText())
        .isEqualTo("CANCELLED");
    balanced();
  }

  @Test
  void futureAndExpiredMandatesCannotExecute() throws Exception {
    String future = mandate("2099-01-01", null);
    postJson("/api/v1/mandates/" + future + "/activate", Map.of(), auth, 200);
    postJson("/api/v1/mandates/" + future + "/execute", execution("1"), auth, 400);
    String expired = mandate("2020-01-01", "2020-02-01");
    postJson("/api/v1/mandates/" + expired + "/activate", Map.of(), auth, 400);
  }

  @Test
  void loansDisburseRepayAndRemainAccessibleToTheBorrower() throws Exception {
    var loan =
        postJson(
            "/api/v1/loans",
            Map.of(
                "accountId",
                Long.valueOf(source),
                "displayName",
                "Validation loan",
                "principal",
                "500",
                "interestRate",
                "10.5"),
            auth,
            200);
    String id = loan.get("PRODUCT_ID").asText(), account = loan.get("ID").asText();
    assertThat(balance(account)).isZero();
    postJson("/api/v1/loans/" + id + "/disburse", Map.of(), otherAuth, 404);
    var disbursed = postJson("/api/v1/loans/" + id + "/disburse", Map.of(), auth, 200);
    assertThat(postJson("/api/v1/loans/" + id + "/disburse", Map.of(), auth, 200))
        .isEqualTo(disbursed);
    assertThat(balance(source)).isEqualByComparingTo("1500");
    assertThat(balance(account)).isEqualByComparingTo("500");
    var request = execution("200");
    postJson("/api/v1/loans/" + id + "/repay", request, auth, 200);
    postJson("/api/v1/loans/" + id + "/repay", request, auth, 200);
    assertThat(new BigDecimal(getJson("/api/v1/loans/" + id, auth).get("outstanding").asText()))
        .isEqualByComparingTo("300");
    assertThat(getJson("/api/v1/loans/" + id + "/payments", auth).size()).isEqualTo(1);
    postJson("/api/v1/loans/" + id + "/repay", execution("301"), auth, 400);
    postJson("/api/v1/loans/" + id + "/repay", execution("300"), auth, 200);
    assertThat(getJson("/api/v1/loans/" + id, auth).get("status").asText()).isEqualTo("PAID");
    String relogin =
        "Bearer "
            + postJson(
                    "/api/v1/auth/login",
                    Map.of("email", email, "password", "SixTable@Test123"),
                    null,
                    200)
                .get("accessToken")
                .asText();
    assertThat(
            getJson("/api/v1/accounts/" + account + "/balance", relogin)
                .get("availableBalance")
                .decimalValue())
        .isZero();
    balanced();
  }

  @Test
  void postingFailureRollsBackLoanAndMandateAtomically() throws Exception {
    var loan =
        postJson(
            "/api/v1/loans",
            Map.of(
                "accountId",
                Long.valueOf(source),
                "displayName",
                "Rollback loan",
                "principal",
                "73.21",
                "interestRate",
                "0"),
            auth,
            200);
    String id = loan.get("PRODUCT_ID").asText();
    String mandate = mandate("2020-01-01", null);
    postJson("/api/v1/mandates/" + mandate + "/activate", Map.of(), auth, 200);
    db.execute(
        "ALTER TABLE ledger_entries ADD CONSTRAINT six_test_failure CHECK(amount<>73.21 OR"
            + " account_id NOT IN ("
            + Long.parseLong(source)
            + ","
            + Long.parseLong(destination)
            + ","
            + loan.get("ID").asLong()
            + "))");
    try {
      postJson("/api/v1/loans/" + id + "/disburse", Map.of(), auth, 503);
      postJson("/api/v1/mandates/" + mandate + "/execute", execution("73.21"), auth, 503);
    } finally {
      db.execute("ALTER TABLE ledger_entries DROP CONSTRAINT six_test_failure");
    }
    assertThat(balance(source)).isEqualByComparingTo("1000");
    assertThat(balance(destination)).isZero();
    assertThat(balance(loan.get("ID").asText())).isZero();
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE target_id=? OR parent_id=? AND"
                    + " record_kind='PAYMENT'",
                Integer.class,
                id,
                mandate))
        .isZero();
    postJson("/api/v1/loans/" + id + "/disburse", Map.of(), auth, 200);
    db.execute(
        "ALTER TABLE ledger_entries ADD CONSTRAINT six_test_failure CHECK(amount<>73.21 OR"
            + " entry_type<>'CREDIT' OR account_id<>"
            + loan.get("ID").asLong()
            + ")");
    try {
      postJson("/api/v1/loans/" + id + "/repay", execution("73.21"), auth, 503);
    } finally {
      db.execute("ALTER TABLE ledger_entries DROP CONSTRAINT six_test_failure");
    }
    assertThat(balance(source)).isEqualByComparingTo("1073.21");
    assertThat(balance(loan.get("ID").asText())).isEqualByComparingTo("73.21");
    balanced();
  }

  @Test
  void onlySixBankingTablesRemain() {
    List<String> tables =
        ORACLE
            ? db.queryForList(
                "SELECT table_name FROM all_tables WHERE"
                    + " owner=SYS_CONTEXT('USERENV','CURRENT_SCHEMA')",
                String.class)
            : db.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema='PUBLIC'",
                String.class);
    assertThat(
            tables.stream()
                .filter(t -> !t.startsWith("CONVERSATION") && !t.equals("flyway_schema_history"))
                .toList())
        .containsExactlyInAnyOrder(
            "CUSTOMERS",
            "CUSTOMER_CREDENTIALS",
            "ACCOUNTS",
            "TRANSACTIONS",
            "JOURNAL_ENTRIES",
            "LEDGER_ENTRIES");
  }

  @Test
  void concurrentMandateRetriesPostOnceAndRepaymentsCannotOverpay() throws Exception {
    String mandate = mandate("2020-01-01", null);
    postJson("/api/v1/mandates/" + mandate + "/activate", Map.of(), auth, 200);
    var same = execution("50");
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var start = new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.Callable<String> job =
          () -> {
            start.await();
            return postJson("/api/v1/mandates/" + mandate + "/execute", same, auth, 200)
                .get("transactionId")
                .asText();
          };
      var a = pool.submit(job);
      var b = pool.submit(job);
      start.countDown();
      assertThat(a.get(20, java.util.concurrent.TimeUnit.SECONDS))
          .isEqualTo(b.get(20, java.util.concurrent.TimeUnit.SECONDS));
      assertThat(balance(destination)).isEqualByComparingTo("50");
      postJson(
          "/api/v1/mandates/" + mandate + "/execute",
          Map.of("amount", "60", "requestId", same.get("requestId")),
          auth,
          409);
      var loan =
          postJson(
              "/api/v1/loans",
              Map.of(
                  "accountId",
                  Long.valueOf(source),
                  "displayName",
                  "Concurrent loan",
                  "principal",
                  "500",
                  "interestRate",
                  "0"),
              auth,
              200);
      String id = loan.get("PRODUCT_ID").asText();
      postJson("/api/v1/loans/" + id + "/disburse", Map.of(), auth, 200);
      var barrier = new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.Callable<Integer> repay =
          () -> {
            barrier.await();
            return mvc.perform(
                    post("/api/v1/loans/" + id + "/repay")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(execution("400"))))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var c = pool.submit(repay);
      var d = pool.submit(repay);
      barrier.countDown();
      assertThat(
              List.of(
                  c.get(20, java.util.concurrent.TimeUnit.SECONDS),
                  d.get(20, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 400);
      assertThat(balance(loan.get("ID").asText())).isEqualByComparingTo("100");
      balanced();
    } finally {
      pool.shutdownNow();
    }
  }

  void balanced() {
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM (SELECT j.id FROM journal_entries j LEFT JOIN ledger_entries"
                    + " l ON l.journal_entry_id=j.id GROUP BY j.id HAVING COUNT(l.id)<2 OR SUM(CASE"
                    + " WHEN l.entry_type='DEBIT' THEN l.amount ELSE -l.amount END)<>0)",
                Integer.class))
        .isZero();
  }

  @Test
  void existingOracleDemoCustomerStillAuthenticates() throws Exception {
    if (!ORACLE) return;
    postJson(
        "/api/v1/auth/login",
        Map.of("email", "vishal@example.com", "password", "NexaDemo@123"),
        null,
        200);
  }
}
