package com.nexa.api.conversations;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.*;

@SpringBootTest(
    properties = {
      "spring.profiles.active=integration",
      "spring.datasource.url=jdbc:h2:mem:chatfirst;MODE=Oracle;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.show-sql=false",
      "nexa.security.jwt.secret=chat-first-integration-test-secret-long-enough",
      "nexa.cors.allowed-origins=http://localhost:8000"
    })
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatFirstIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
  String auth, chat, source, destination;

  @BeforeAll
  void schema() {
    new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V7__create_conversation_history.sql"),
            new ClassPathResource("db/migration/V8__add_structured_conversation_content.sql"),
            new ClassPathResource("db/migration/V12__conversation_workflows.sql"),
            new ClassPathResource("db/migration/V13__conversation_action_audit.sql"))
        .execute(db.getDataSource());
    db.execute("ALTER TABLE beneficiaries ADD bank_name VARCHAR2(160)");
  }

  @BeforeEach
  void fixture() throws Exception {
    var user =
        postJson(
            "/api/v1/auth/register",
            Map.of(
                "fullName",
                "Chat Customer",
                "email",
                UUID.randomUUID() + "@example.com",
                "password",
                "SecureTest@123"),
            201);
    auth = "Bearer " + user.get("accessToken").asText();
    source = open("Everyday");
    destination = open("Reserve");
    db.update("UPDATE accounts SET balance=10000 WHERE id=?", Long.valueOf(source));
    chat = postJson("/api/v1/conversations", Map.of(), 201).get("id").asText();
  }

  String open(String name) throws Exception {
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
            201)
        .get("id")
        .asText();
  }

  JsonNode postJson(String path, Object body, int status) throws Exception {
    var request =
        post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    if (auth != null) request.header("Authorization", auth);
    return json.readTree(
        mvc.perform(request)
            .andExpect(status().is(status))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  JsonNode say(String text) throws Exception {
    return postJson(
        "/api/v1/conversations/" + chat + "/turns",
        Map.of("clientId", UUID.randomUUID().toString(), "source", "TEXT", "text", text),
        200);
  }

  JsonNode command(String id, String type, String value, String key) throws Exception {
    return postJson(
        "/api/v1/conversations/" + chat + "/actions/" + id,
        Map.of("clientId", key, "type", type, "value", value),
        200);
  }

  String proposal() throws Exception {
    var start = say("Transfer between my accounts");
    String id = start.get("workflow").get("id").asText();
    command(id, "SELECT", destination, UUID.randomUUID().toString());
    command(id, "SELECT", source, UUID.randomUUID().toString());
    assertThat(say("5000").get("workflow").get("status").asText()).isEqualTo("REVIEW");
    return id;
  }

  @Test
  void completeTransferReusesLedgerAndReplayReturnsSameResult() throws Exception {
    String id = proposal();
    assertThat(say("yes").get("workflow").get("status").asText()).isEqualTo("REVIEW");
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source))
        .isEqualTo(10000);
    String key = UUID.randomUUID().toString();
    var result = command(id, "CONFIRM", "", key);
    String reference = result.get("workflow").get("reference").asText();
    assertThat(result.get("workflow").get("status").asText()).isEqualTo("COMPLETED");
    assertThat(command(id, "CONFIRM", "", key)).isEqualTo(result);
    assertThat(
            command(id, "CONFIRM", "", UUID.randomUUID().toString())
                .get("workflow")
                .get("reference")
                .asText())
        .isEqualTo(reference);
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source))
        .isEqualTo(5000);
    assertThat(
            db.queryForObject(
                "SELECT balance FROM accounts WHERE id=?", Integer.class, destination))
        .isEqualTo(5000);
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM transactions WHERE id=?", Integer.class, reference))
        .isEqualTo(1);
    mvc.perform(get("/api/v1/transactions/" + reference).header("Authorization", auth))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/conversations/" + chat + "/turns").header("Authorization", auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].workflow.status").value("COMPLETED"));
  }

  @Test
  void expiryAndCrossUserAccessPreventExecution() throws Exception {
    String id = proposal();
    var w =
        json.readValue(
            db.queryForObject(
                "SELECT state FROM conversation_workflows WHERE id=?", String.class, id),
            Workflow.class);
    var expired =
        new Workflow(
            w.version(),
            w.id(),
            w.operation(),
            w.status(),
            w.field(),
            w.accountId(),
            w.targetId(),
            w.accountLabel(),
            w.targetLabel(),
            w.amount(),
            w.currency(),
            w.message(),
            w.choices(),
            java.time.OffsetDateTime.now().minusMinutes(1),
            true,
            true,
            null);
    db.update(
        "UPDATE conversation_workflows SET state=? WHERE id=?",
        json.writeValueAsString(expired),
        id);
    assertThat(
            command(id, "CONFIRM", "", UUID.randomUUID().toString())
                .get("workflow")
                .get("status")
                .asText())
        .isEqualTo("EXPIRED");
    mvc.perform(
            post("/api/v1/conversations/" + chat + "/actions/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
    var other =
        postJson(
            "/api/v1/auth/register",
            Map.of(
                "fullName",
                "Other User",
                "email",
                UUID.randomUUID() + "@example.com",
                "password",
                "SecureTest@123"),
            201);
    auth = "Bearer " + other.get("accessToken").asText();
    postJson(
        "/api/v1/conversations/" + chat + "/actions/" + id,
        Map.of("clientId", UUID.randomUUID().toString(), "type", "CONFIRM"),
        404);
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source))
        .isEqualTo(10000);
  }

  @Test
  void clarificationInvalidAmountsAndCancellation() throws Exception {
    String id = say("Transfer between my accounts").get("workflow").get("id").asText();
    assertThat(say("Zzzz").get("workflow").get("field").asText()).isEqualTo("target");
    postJson(
        "/api/v1/conversations/" + chat + "/actions/" + id,
        Map.of("clientId", UUID.randomUUID().toString(), "type", "CONFIRM"),
        400);
    command(id, "SELECT", destination, UUID.randomUUID().toString());
    command(id, "SELECT", source, UUID.randomUUID().toString());
    for (String invalid : List.of("-1", "0", "0.001", "10001"))
      assertThat(say(invalid).get("workflow").get("status").asText()).isEqualTo("COLLECTING");
    say("show my balance");
    assertThat(say("100").get("workflow").get("status").asText()).isEqualTo("REVIEW");
    assertThat(
            command(id, "CANCEL", "", UUID.randomUUID().toString())
                .get("workflow")
                .get("status")
                .asText())
        .isEqualTo("CANCELLED");
    postJson(
        "/api/v1/conversations/" + chat + "/actions/" + id,
        Map.of("clientId", UUID.randomUUID().toString(), "type", "CONFIRM"),
        400);
  }

  @Test
  void concurrentConfirmationsPostOnlyOnce() throws Exception {
    String id = proposal();
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var first = pool.submit(() -> command(id, "CONFIRM", "", UUID.randomUUID().toString()));
      var second = pool.submit(() -> command(id, "CONFIRM", "", UUID.randomUUID().toString()));
      assertThat(first.get().get("workflow").get("reference"))
          .isEqualTo(second.get().get("workflow").get("reference"));
      assertThat(
              db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source))
          .isEqualTo(5000);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void confirmationRevalidatesBalanceAndKeepsProposalRecoverable() throws Exception {
    String id = proposal();
    db.update("UPDATE accounts SET balance=100 WHERE id=?", source);
    postJson(
        "/api/v1/conversations/" + chat + "/actions/" + id,
        Map.of("clientId", UUID.randomUUID().toString(), "type", "CONFIRM"),
        400);
    assertThat(
            db.queryForObject(
                "SELECT balance FROM accounts WHERE id=?", Integer.class, destination))
        .isZero();
    assertThat(
            command(id, "CANCEL", "", UUID.randomUUID().toString())
                .get("workflow")
                .get("status")
                .asText())
        .isEqualTo("CANCELLED");
  }

  @Test
  void sensitiveInputIsNotRetainedAndAuditSurvivesHistoryDeletion() throws Exception {
    var response = say("my password is super-secret-123");
    assertThat(response.get("userText").asText()).isEqualTo("Sensitive message removed.");
    assertThat(response.toString()).doesNotContain("super-secret-123");
    String id = proposal();
    command(id, "CONFIRM", "", UUID.randomUUID().toString());
    mvc.perform(delete("/api/v1/conversations/" + chat).header("Authorization", auth))
        .andExpect(status().isNoContent());
    assertThat(
            db.queryForObject(
                "SELECT count(*) FROM conversation_action_events WHERE action_id=? AND"
                    + " status='COMPLETED'",
                Integer.class,
                id))
        .isEqualTo(1);
  }

  @Test
  void spendingIncludesAllCompletedOutgoingAndExcludesOwnTransfers() throws Exception {
    String id = proposal();
    command(id, "CONFIRM", "", UUID.randomUUID().toString());
    db.update(
        "INSERT INTO"
            + " transactions(id,transaction_type,source_account_id,amount,status,created_at,category)"
            + " VALUES (?,'WITHDRAWAL',?,42.50,'SUCCESS',CURRENT_TIMESTAMP,'food')",
        "TX-" + UUID.randomUUID(),
        source);
    var result = say("Show my spending on food this month");
    assertThat(result.get("banking").get("type").asText()).isEqualTo("INSIGHTS");
    assertThat(result.get("banking").get("meta").get("categories").get(0).get("amount").asText())
        .isEqualTo("42.50");
  }

  @Test
  void ambiguousBeneficiariesRequireSelectionAndCannotExecute() throws Exception {
    String owner =
        db.queryForObject(
            "SELECT c.user_id FROM customers c JOIN accounts a ON a.customer_id=c.id WHERE a.id=?",
            String.class,
            source);
    for (String suffix : List.of("Sharma", "Verma")) {
      db.update(
          "INSERT INTO"
              + " beneficiaries(id,user_id,display_name,beneficiary_type,account_holder_name,destination_account_masked,destination_account_hash,status,created_at,updated_at,version,bank_name)"
              + " VALUES"
              + " (?,?,?,'BANK_ACCOUNT',?,'1234',?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'Nexa')",
          "ben_" + suffix,
          owner,
          "Rahul " + suffix,
          "Rahul " + suffix,
          "0".repeat(64));
    }
    var start = say("Transfer 5000 to Rahul");
    String id = start.get("workflow").get("id").asText();
    assertThat(start.get("workflow").get("choices").size()).isEqualTo(2);
    assertThat(start.get("workflow").get("message").asText()).contains("more than one");
    command(id, "SELECT", "ben_Sharma", UUID.randomUUID().toString());
    var review = command(id, "SELECT", source, UUID.randomUUID().toString());
    assertThat(review.get("workflow").get("status").asText()).isEqualTo("UNAVAILABLE");
    postJson(
        "/api/v1/conversations/" + chat + "/actions/" + id,
        Map.of("clientId", UUID.randomUUID().toString(), "type", "CONFIRM"),
        400);
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source))
        .isEqualTo(10000);
  }

  @Test
  void expiredTokenAndWrongRoleCannotAccessConversationActions() throws Exception {
    String id = proposal();
    mvc.perform(
            post("/api/v1/conversations/" + chat + "/actions/" + id)
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_VIEWER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    var claims =
        org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
            .issuer("nexa-api")
            .subject("expired-user")
            .issuedAt(java.time.Instant.now().minusSeconds(7200))
            .expiresAt(java.time.Instant.now().minusSeconds(3600))
            .claim("roles", List.of("CUSTOMER"))
            .build();
    var header =
        org.springframework.security.oauth2.jwt.JwsHeader.with(
                org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
            .build();
    String expired =
        encoder
            .encode(
                org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(header, claims))
            .getTokenValue();
    mvc.perform(get("/api/v1/conversations").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized());
  }
}
