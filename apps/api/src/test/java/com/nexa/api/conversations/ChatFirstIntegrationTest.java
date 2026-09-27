package com.nexa.api.conversations;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nexa.api.service.Workflow;
import db.migration.V17__migrate_banking_products;
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
public class ChatFirstIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired com.nexa.api.service.AccountService accountFixtures;
  @Autowired com.nexa.api.repository.CustomerDao customerFixtures;
  @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
  String auth, chat, source, destination;

  @Test
  void knowledgeQuestionCannotCreateOrConfirmAWorkflow() throws Exception {
    var first = say("What is a personal loan?");
    assertThat(first.get("intent").asText()).isEqualTo("KNOWLEDGE");
    assertThat(say("What is the interest rate?").get("assistantText").asText()).contains("14.50%");
    assertThat(say("What documents do I need?").get("assistantText").asText()).contains("three months");
    var explanation = say("How do I freeze my card?");
    assertThat(explanation.path("workflow").isNull() || explanation.path("workflow").isMissingNode()).isTrue();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM conversation_workflows WHERE conversation_id=?", Integer.class, chat)).isZero();
    payee("KB recipient");
    var review = say("transfer 40 to KB recipient from Everyday").get("workflow");
    assertThat(review.get("status").asText()).isEqualTo("REVIEW");
    var duringReview = say("What is a transfer?");
    assertThat(duringReview.get("intent").asText()).isEqualTo("KNOWLEDGE");
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source)).isEqualTo(10000);
    var confirmed = command(review.get("id").asText(), "CONFIRM", "", UUID.randomUUID().toString());
    assertThat(confirmed.get("workflow").get("status").asText()).isEqualTo("COMPLETED");
  }

  @BeforeAll
  void schema() {
    new ResourceDatabasePopulator(
            new ClassPathResource("six-table-chat.sql"),
            new ClassPathResource("db/migration/V8__add_structured_conversation_content.sql"),
            new ClassPathResource("db/migration/V12__conversation_workflows.sql"),
            new ClassPathResource("db/migration/V13__conversation_action_audit.sql"),
            new ClassPathResource("db/migration/V27__bill_payment_attempts.sql"))
        .execute(db.getDataSource());
  }

  public static String jsonValue(String payload, String path) {
    return tools.jackson.databind.json.JsonMapper.builder()
        .build()
        .readTree(payload)
        .path("status")
        .asText();
  }

  String namedProduct(String kind, String name) {
    String id = demoProduct(kind);
    if (kind.equals("CARD"))
      db.update("UPDATE accounts SET account_name=? WHERE product_id=?", name, id);
    else db.update("UPDATE transactions SET display_name=? WHERE id=?", name, id);
    return id;
  }

  String payee(String name) {
    String id = "ben_" + UUID.randomUUID().toString().substring(0, 8);
    String owner =
        db.queryForObject(
            "SELECT c.user_id FROM customers c JOIN accounts a ON a.customer_id=c.id WHERE a.id=?",
            String.class,
            source);
    db.update(
        "INSERT INTO"
            + " transactions(id,user_id,display_name,beneficiary_type,recipient_name,destination_masked,destination_hash,status,created_at,updated_at,record_kind,bank_name)"
            + " VALUES"
            + " (?,?,?,'BANK_ACCOUNT',?,'1234',?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'BENEFICIARY','Nexa')",
        id,
        owner,
        name,
        name,
        destinationHash());
    db.update("UPDATE transactions SET destination_account_id=? WHERE id=?", destination, id);
    return id;
  }

  String destinationHash() {
    try {
      return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
          .digest(("NEXA:" + destination).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  @Test
  void hinglishTransferKeepsPayeeAmountAndSourceAcrossCorrections() throws Exception {
    String rahul = payee("Rahul Sharma");
    payee("Asha Rao");
    var review = say("Rahul ko 500 bhejo Everyday se").get("workflow");
    assertThat(review.get("status").asText()).isEqualTo("REVIEW");
    assertThat(review.get("targetId").asText()).isEqualTo(rahul);
    assertThat(review.get("accountId").asText()).isEqualTo(source);
    assertThat(review.get("amount").asText()).isEqualTo("500");
    String original = review.get("id").asText();
    var invalid = say("make it 1e3").get("workflow");
    assertThat(invalid.get("field").asText()).isEqualTo("amount");
    assertThat(invalid.get("id").asText()).isNotEqualTo(original);
    assertThat(say("1000").get("workflow").get("targetId").asText()).isEqualTo(rahul);
    assertThat(say("yes").get("workflow").get("amount").asText()).isEqualTo("1000");
    assertThat(say("nahi cancel karo").get("workflow").get("status").asText())
        .isEqualTo("CANCELLED");
  }

  @Test
  void ambiguousAndMissingCorrectionsCannotConfirmTheOldRecipient() throws Exception {
    namedProduct("BILL", "Electricity");
    namedProduct("BILL", "Water North");
    namedProduct("BILL", "Water South");
    var first = say("pay electricity bill from Everyday").get("workflow");
    var changed = say("pay water bill instead").get("workflow");
    assertThat(changed.get("status").asText()).isEqualTo("COLLECTING");
    assertThat(changed.get("choices").size()).isEqualTo(2);
    assertThat(changed.get("accountId").asText()).isEqualTo(source);
    assertThat(say("haan").get("workflow").get("field").asText()).isEqualTo("target");
    var selected = say("Water North").get("workflow");
    assertThat(selected.get("status").asText()).isEqualTo("REVIEW");
    var missing = say("pay internet bill").get("workflow");
    assertThat(missing.get("field").asText()).isEqualTo("target");
    postJson(
        "/api/v1/conversations/" + chat + "/actions/" + first.get("id").asText(),
        Map.of("clientId", UUID.randomUUID().toString(), "type", "CONFIRM"),
        400);
  }

  @Test
  void readFiltersPersistThroughSeveralFollowupsAndReadInterruption() throws Exception {
    say("show transactions from last month");
    var debit = say("only debit transactions").get("banking");
    String from = debit.get("meta").get("query").get("from").asText();
    var again = say("show it").get("banking");
    assertThat(again.get("meta").get("query").get("from").asText()).isEqualTo(from);
    assertThat(again.get("meta").get("query").get("direction").asText()).isEqualTo("DEBIT");
    namedProduct("BILL", "Electricity");
    say("pay electricity bill");
    say("balance batao");
    assertThat(say("show it").get("banking").get("type").asText()).isEqualTo("ACCOUNTS");
    assertThat(say("haan kar do").get("workflow").get("targetLabel").asText())
        .contains("Electricity");
  }

  @Test
  void aNewConversationDoesNotInheritAnotherConversationsPayment() throws Exception {
    String bill = namedProduct("BILL", "Electricity");
    say("pay electricity bill");
    String firstChat = chat;
    chat = postJson("/api/v1/conversations", Map.of(), 201).get("id").asText();
    var reply = say("haan kar do");
    assertThat(reply.get("workflow").isNull()).isTrue();
    assertThat(reply.get("assistantText").asText())
        .isEqualTo("What would you like me to help with?");
    chat = firstChat;
    assertThat(say("bill pay karo").get("workflow").get("targetId").asText()).isEqualTo(bill);
  }

  @Test
  void electricityFollowupsKeepTheBillAndAmountUntilExplicitConfirmation() throws Exception {
    String electricity = namedProduct("BILL", "Electricity");
    namedProduct("BILL", "Water");
    var first = say("pay electricity bill").get("workflow");
    assertThat(first.get("targetId").asText()).isEqualTo(electricity);
    assertThat(first.get("field").asText()).isEqualTo("account");
    assertThat(new java.math.BigDecimal(first.get("amount").asText())).isEqualByComparingTo("100");
    for (String followup : List.of("bill pay karo", "haan", "do it", "haan kar do")) {
      var continued = say(followup).get("workflow");
      assertThat(continued.get("id")).isEqualTo(first.get("id"));
      assertThat(continued.get("targetId").asText()).isEqualTo(electricity);
      assertThat(continued.get("message").asText()).contains("Electricity", "which account");
      assertThat(new java.math.BigDecimal(continued.get("amount").asText())).isEqualByComparingTo("100");
    }
    var review = say("Everyday se").get("workflow");
    assertThat(review.get("status").asText()).isEqualTo("REVIEW");
    for (String followup : List.of("yes", "pay karo", "confirm", "हाँ कर दो"))
      assertThat(say(followup).get("workflow").get("status").asText()).isEqualTo("REVIEW");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE status='SIMULATED' AND target_id=?",
                Integer.class,
                electricity))
        .isZero();
    var result = command(review.get("id").asText(), "CONFIRM", "", UUID.randomUUID().toString());
    assertThat(result.get("assistantText").asText())
        .contains("100", "Electricity", "Bill payment posted", "Chat Customer")
        .doesNotContain("simulation", "no money moved", "COLLECTING");
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source)).isEqualTo(9900);
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, destination)).isEqualTo(100);
    mvc.perform(get("/api/v1/bills/" + electricity).header("Authorization", auth))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.outstandingAmount").value("0.00"));
    assertThat(result.get("workflow").get("reference").asText()).startsWith("TX-");
    assertThat(say("do it").get("workflow").get("reference"))
        .isEqualTo(result.get("workflow").get("reference"));
  }

  @Test
  void hindiVoiceAndTypedRequestsUseTheSameState() throws Exception {
    String electricity = namedProduct("BILL", "Electricity");
    var spoken =
        postJson(
            "/api/v1/conversations/" + chat + "/turns",
            Map.of(
                "clientId",
                UUID.randomUUID().toString(),
                "source",
                "VOICE",
                "text",
                "बिजली का बिल भुगतान करो"),
            200);
    assertThat(spoken.get("workflow").get("targetId").asText()).isEqualTo(electricity);
    assertThat(say("bill pay karo").get("workflow").get("targetId").asText())
        .isEqualTo(electricity);
    assertThat(say("balance batao").get("banking").get("type").asText()).isEqualTo("ACCOUNTS");
    assertThat(say("haan kar do").get("workflow").get("field").asText()).isEqualTo("account");
    assertThat(say("नहीं रद्द करो").get("workflow").get("status").asText()).isEqualTo("CANCELLED");
    assertThat(say("मेरे खाते का बैलेंस बताइए").get("banking").get("type").asText())
        .isEqualTo("ACCOUNTS");
  }

  @Test
  void correctionsReplaceConfirmationAndRetainUnchangedFields() throws Exception {
    String id = proposal();
    var revised = say("make it 250").get("workflow");
    assertThat(revised.get("id").asText()).isNotEqualTo(id);
    assertThat(revised.get("amount").asText()).isEqualTo("250");
    assertThat(revised.get("accountId").asText()).isEqualTo(source);
    assertThat(revised.get("targetId").asText()).isEqualTo(destination);
    postJson(
        "/api/v1/conversations/" + chat + "/actions/" + id,
        Map.of("clientId", UUID.randomUUID().toString(), "type", "CONFIRM"),
        400);
    assertThat(say("nahi cancel karo").get("workflow").get("status").asText())
        .isEqualTo("CANCELLED");
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source))
        .isEqualTo(10000);
  }

  @Test
  void changedBillsKeepSourceAndUseTheNewBillsAmount() throws Exception {
    namedProduct("BILL", "Electricity");
    String water = namedProduct("BILL", "Water");
    var first = say("pay electricity bill from Everyday").get("workflow");
    assertThat(first.get("status").asText()).isEqualTo("REVIEW");
    var changed = say("actually pay water bill").get("workflow");
    assertThat(changed.get("targetId").asText()).isEqualTo(water);
    assertThat(changed.get("accountId").asText()).isEqualTo(source);
    assertThat(changed.get("id")).isNotEqualTo(first.get("id"));
    assertThat(say("change account").get("workflow").get("field").asText()).isEqualTo("account");
    assertThat(say("bill pay karo").get("workflow").get("targetId").asText()).isEqualTo(water);
  }

  @Test
  void legacyCardPaymentsAndMandateActionsStayInConversation() throws Exception {
    String card = namedProduct("CARD", "Travel");
    namedProduct("MANDATE", "Music");
    for (String request :
        List.of(
            "pay Travel card from Everyday",
            "cancel Music mandate")) {
      var review = say(request).get("workflow");
      assertThat(review.get("status").asText()).as(request).isEqualTo("REVIEW");
      assertThat(say("haan kar do").get("workflow").get("id")).isEqualTo(review.get("id"));
      var result = command(review.get("id").asText(), "CONFIRM", "", UUID.randomUUID().toString());
      assertThat(result.get("assistantText").asText()).contains("accepted").doesNotContain("Open");
      assertThat(result.get("assistantText").asText()).contains("Simulation recorded");
    }
  }

  @Test
  void readFollowupsAndPayItResolveTheLastDisplayedBill() throws Exception {
    String bill = namedProduct("BILL", "Electricity");
    var list = say("show my bills");
    assertThat(list.get("workflow").isNull()).isTrue();
    assertThat(say("show it").get("banking").get("bills").get(0).get("id").asText())
        .isEqualTo(bill);
    var payment = say("pay it").get("workflow");
    assertThat(payment.get("targetId").asText()).isEqualTo(bill);
    assertThat(payment.get("field").asText()).isEqualTo("account");
  }

  @Test
  void applyingForALoanReturnsTheApplicationFormWithoutCreatingOrDisbursingALoan() throws Exception {
    assertThat(say("show loans").get("intent").asText()).isEqualTo("GET_LOANS");
    int documentsBefore = db.queryForObject("SELECT COUNT(*) FROM loan_salary_slips", Integer.class);
    for (String text : List.of("apply for loan", "I want to apply for a loan", "loan apply karo", "ऋण के लिए आवेदन")) {
      var response = say(text);
      assertThat(response.get("intent").asText()).as(text).isEqualTo("APPLY_LOAN");
      assertThat(response.get("banking").get("type").asText()).isEqualTo("LOAN_APPLICATION");
      assertThat(response.get("response").get("type").asText()).isEqualTo("LOAN_APPLICATION");
      assertThat(response.get("workflow").isNull()).isTrue();
      assertThat(response.get("assistantText").asText()).contains("salary slips", "when you submit");
    }
    assertThat(db.queryForObject("SELECT COUNT(*) FROM accounts WHERE account_type='LOAN' AND customer_id="
        + "(SELECT customer_id FROM accounts WHERE id=?)", Integer.class, source)).isZero();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM loan_salary_slips", Integer.class)).isEqualTo(documentsBefore);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM conversation_workflows WHERE conversation_id=?", Integer.class, chat)).isZero();
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source)).isEqualTo(10000);
    assertThat(say("show loans").get("intent").asText()).isEqualTo("GET_LOANS");
    assertThat(say("repay loan").get("workflow").get("operation").asText()).isEqualTo("REPAY_LOAN");
  }

  @Test
  void openingLoanApplicationPreservesAPendingTransferWithoutConfirmingIt() throws Exception {
    String action = proposal();
    String before = db.queryForObject("SELECT state FROM conversation_workflows WHERE id=?", String.class, action);
    var response = say("apply for a loan");
    assertThat(response.get("intent").asText()).isEqualTo("APPLY_LOAN");
    assertThat(response.get("workflow").isNull()).isTrue();
    assertThat(db.queryForObject("SELECT state FROM conversation_workflows WHERE id=?", String.class, action)).isEqualTo(before);
    var continued = say("yes").get("workflow");
    assertThat(continued.get("id").asText()).isEqualTo(action);
    assertThat(continued.get("status").asText()).isEqualTo("REVIEW");
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source)).isEqualTo(10000);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM transactions WHERE record_kind='PAYMENT' AND source_account_id=?", Integer.class, source)).isZero();
  }

  @Test
  void loanApplicationInformationNegationAndCompoundRequestsDoNotOpenOrSubmitForms() throws Exception {
    for (String text : List.of("How do I apply for a loan?", "Can I apply for a loan?", "don't apply for loan",
        "apply for loan tomorrow", "apply for loan and transfer 500")) {
      var response = say(text);
      assertThat(response.get("intent").asText()).as(text).isNotEqualTo("APPLY_LOAN");
      assertThat(response.path("banking").path("type").asText()).isNotEqualTo("LOAN_APPLICATION");
    }
    assertThat(db.queryForObject("SELECT COUNT(*) FROM accounts WHERE account_type='LOAN' AND customer_id="
        + "(SELECT customer_id FROM accounts WHERE id=?)", Integer.class, source)).isZero();
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source)).isEqualTo(10000);
  }

  @Test
  void billCommandsStartANewBillSelectionAfterClosedWorkflowAndLoanHistory() throws Exception {
    String water = namedProduct("BILL", "Water");
    String electricity = namedProduct("BILL", "Electricity");
    for (String text : List.of("pay bill", "paybill", "pay bills")) {
      var previous = say("pay water bill").get("workflow");
      assertThat(say("cancel").get("workflow").get("status").asText()).isEqualTo("CANCELLED");
      assertThat(say("show loans").get("intent").asText()).isEqualTo("GET_LOANS");
      var current = say(text);
      assertThat(current.get("intent").asText()).as(text).isEqualTo("PAY_BILL");
      var workflow = current.get("workflow");
      assertThat(workflow.get("id").asText()).isNotEqualTo(previous.get("id").asText());
      assertThat(workflow.get("operation").asText()).isEqualTo("PAY_BILL");
      assertThat(workflow.get("status").asText()).isEqualTo("COLLECTING");
      assertThat(workflow.get("field").asText()).isEqualTo("target");
      var choices = new ArrayList<String>();
      workflow.get("choices").forEach(choice -> choices.add(choice.get("id").asText()));
      assertThat(choices).containsExactlyInAnyOrder(water, electricity);
      assertThat(current.get("assistantText").asText()).doesNotContain("loans", "EMIs");
      var selected = command(workflow.get("id").asText(), "SELECT", water, UUID.randomUUID().toString()).get("workflow");
      assertThat(selected.get("targetId").asText()).isEqualTo(water);
      assertThat(selected.get("field").asText()).isEqualTo("account");
    }
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source)).isEqualTo(10000);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM transactions WHERE record_kind='PAYMENT' AND source_account_id=?", Integer.class, source)).isZero();
  }

  @Test
  void balanceInterruptsBillSelectionWithoutLosingTheBillOrReusingLoanContext() throws Exception {
    String water = namedProduct("BILL", "Water");
    say("show loans");
    var selection = say("paybill").get("workflow");
    assertThat(selection.get("field").asText()).isEqualTo("target");
    var initial = command(selection.get("id").asText(), "SELECT", water, UUID.randomUUID().toString()).get("workflow");
    assertThat(initial.get("targetId").asText()).isEqualTo(water);
    for (String text : List.of("balance", "show my balance", "balance batao")) {
      var balance = say(text);
      assertThat(balance.get("intent").asText()).as(text).isEqualTo("GET_BALANCE");
      assertThat(balance.get("banking").get("type").asText()).isEqualTo("ACCOUNTS");
      assertThat(balance.get("workflow").isNull()).isTrue();
    }
    var resumed = say("pay bill").get("workflow");
    assertThat(resumed.get("id").asText()).isEqualTo(initial.get("id").asText());
    assertThat(resumed.get("targetId").asText()).isEqualTo(water);
    assertThat(resumed.get("field").asText()).isEqualTo("account");
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source)).isEqualTo(10000);
  }

  @Test
  void explicitBillCommandRestartsAnExpiredSelectionButCannotConfirmItsOldReview() throws Exception {
    namedProduct("BILL", "Water");
    var previous = say("pay water bill").get("workflow");
    String id = previous.get("id").asText();
    var state = (tools.jackson.databind.node.ObjectNode) json.readTree(
        db.queryForObject("SELECT state FROM conversation_workflows WHERE id=?", String.class, id));
    state.put("expiresAt", java.time.OffsetDateTime.now().minusMinutes(1).toString());
    db.update("UPDATE conversation_workflows SET state=? WHERE id=?", json.writeValueAsString(state), id);
    var restarted = say("paybill").get("workflow");
    assertThat(restarted.get("operation").asText()).isEqualTo("PAY_BILL");
    assertThat(restarted.get("status").asText()).isEqualTo("COLLECTING");
    assertThat(restarted.get("id").asText()).isNotEqualTo(id);
    postJson("/api/v1/conversations/" + chat + "/actions/" + id,
        Map.of("clientId", UUID.randomUUID().toString(), "type", "CONFIRM"), 400);
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source)).isEqualTo(10000);
  }

  @Test
  void historyAndBalanceFollowupsKeepTheSelectedAccount() throws Exception {
    say("show my balance");
    var onlyReserve = say("what about Reserve").get("banking");
    assertThat(onlyReserve.get("accounts").size()).isEqualTo(1);
    assertThat(onlyReserve.get("accounts").get(0).get("id").asText()).isEqualTo(destination);
    assertThat(say("show transactions").get("banking").get("account").get("id").asText())
        .isEqualTo(destination);
    var lastMonth = say("last month").get("banking");
    assertThat(lastMonth.get("type").asText()).isEqualTo("TRANSACTIONS");
    assertThat(say("show it").get("banking").get("account").get("id"))
        .isEqualTo(lastMonth.get("account").get("id"));
  }

  @Test
  void repeatedTransactionDetailKeepsTheSelectedReference() throws Exception {
    String reference = "TX-" + UUID.randomUUID();
    db.update(
        "INSERT INTO"
            + " transactions(id,transaction_type,source_account_id,amount,status,created_at,category)"
            + " VALUES (?,'WITHDRAWAL',?,42.50,'SUCCESS',CURRENT_TIMESTAMP,'food')",
        reference,
        source);
    say("show transactions");
    var first = say("first one").get("banking");
    assertThat(first.get("transactions").get(0).get("id").asText()).isEqualTo(reference);
    var repeated = say("show it").get("banking");
    assertThat(repeated.get("transactions").get(0).get("id").asText()).isEqualTo(reference);
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
    // Fixture setup bypasses the retired instant-opening route; onboarding has its own integration tests.
    var profile = json.readTree(mvc.perform(get("/api/v1/me").header("Authorization", auth))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    var account = new com.nexa.api.beans.Account();
    account.setCustomer(customerFixtures.findByUserId(profile.get("userId").asText()).orElseThrow());
    account.setAccountNumber("9" + String.format("%011d", java.util.concurrent.ThreadLocalRandom.current().nextLong(100000000000L)));
    account.setAccountName(name);
    account.setAccountType(com.nexa.api.beans.AccountType.SAVINGS);
    account.setAccountCategory(com.nexa.api.beans.AccountCategory.CUSTOMER);
    account.setCurrencyCode("INR");
    return accountFixtures.create(account).getId().toString();
  }

  JsonNode postJson(String path, Object body, int status) throws Exception {
    var request =
        path.equals("/api/v1/loans")
            ? com.nexa.api.banking.LoanTestSupport.application(json.writeValueAsString(body))
            : post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
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

  String productSnapshot(String kind, String id) {
    return json.writeValueAsString(
        db.queryForMap(
            kind.equals("CARD")
                ? "SELECT * FROM accounts WHERE product_id=?"
                : "SELECT * FROM transactions WHERE id=?",
            id));
  }

  String demoProduct(String kind) {
    String id = UUID.randomUUID().toString();
    String owner =
        db.queryForObject(
            "SELECT c.user_id FROM customers c JOIN accounts a ON a.customer_id=c.id WHERE a.id=?",
            String.class,
            source);
    var payload = new HashMap<String, Object>();
    payload.put("id", id);
    payload.put("accountId", source);
    payload.put("currencyCode", "INR");
    payload.put("status", kind.equals("BILL") ? "DUE" : "ACTIVE");
    if (kind.equals("CARD")) {
      payload.put("cardType", "CREDIT");
      payload.put("outstanding", "500");
    }
    if (kind.equals("BILL")) payload.put("amount", "100");
    payload.put("displayName", "Test product");
    payload.put("creditLimit", "1000");
    payload.put("minimumPayment", "10");
    V17__migrate_banking_products.importProduct(
        db, owner, kind, Long.parseLong(source), json.valueToTree(payload));
    if (kind.equals("BILL")) {
      String recipient = payee("Bill recipient " + id);
      db.update("UPDATE transactions SET target_id=?,destination_account_id=?,due_at=? WHERE id=?",
          recipient, Long.parseLong(destination), "2026-09-23", id);
    }
    return id;
  }

  @Test
  void billSimulationEndpointsCannotExecuteNewOrCachedReviews() throws Exception {
    String bill = demoProduct("BILL");
    postJson("/api/v1/demo/actions/prepare", Map.of("operation", "PAY_BILL",
        "accountId", source, "targetId", bill, "amount", "100"), 400);
    String cached = postJson("/api/v1/demo/actions/prepare", Map.of("operation", "PAY_CARD",
        "accountId", source, "targetId", demoProduct("CARD"), "amount", "100"), 200).get("id").asText();
    db.update("UPDATE transactions SET operation='PAY_BILL',target_id=? WHERE id=?", bill, cached);
    postJson("/api/v1/demo/actions/" + cached + "/confirm", Map.of(), 400);
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source)).isEqualTo(10000);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM bill_payment_attempts WHERE bill_id=?", Integer.class, bill)).isZero();
  }

  @Test
  void cardControlsDirectCustomersToPersistedControlsWithoutSimulation() throws Exception {
    String card = namedProduct("CARD", "Travel");
    String before = productSnapshot("CARD", card);
    for (String request : List.of("freeze Travel card", "unfreeze Travel card", "replace Travel card")) {
      var workflow = say(request).path("workflow");
      assertThat(workflow.path("status").asText()).isEqualTo("UNAVAILABLE");
      assertThat(workflow.path("operation").asText()).isEqualTo("CARD_CONTROL");
      assertThat(workflow.path("executionAvailable").asBoolean()).isFalse();
      assertThat(workflow.path("message").asText()).contains("Open Cards");
    }
    assertThat(productSnapshot("CARD", card)).isEqualTo(before);
  }

  @Test
  void unlinkedBillChatExplainsHowToChooseARealRecipient() throws Exception {
    String bill = namedProduct("BILL", "Electricity");
    db.update("UPDATE transactions SET target_id=NULL,destination_account_id=NULL WHERE id=?", bill);
    var result = say("pay electricity bill from Everyday").get("workflow");
    assertThat(result.get("status").asText()).isEqualTo("UNAVAILABLE");
    assertThat(result.get("message").asText()).contains("Pay now");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM bill_payment_attempts WHERE bill_id=?", Integer.class, bill)).isZero();
  }

  @Test
  void invalidBillAmountAndInsufficientFundsRemainRecoverableChatPrompts() throws Exception {
    namedProduct("BILL", "Electricity");
    var invalid = say("pay 200 electricity bill from Everyday").get("workflow");
    assertThat(invalid.get("status").asText()).isEqualTo("COLLECTING");
    assertThat(say("100").get("workflow").get("status").asText()).isEqualTo("REVIEW");
    say("cancel");
    db.update("UPDATE accounts SET balance=0 WHERE id=?", source);
    var insufficient = say("pay electricity bill from Everyday").get("workflow");
    assertThat(insufficient.get("status").asText()).isEqualTo("COLLECTING");
    assertThat(insufficient.get("message").asText()).containsIgnoringCase("not enough money");
    db.update("UPDATE accounts SET balance=10000 WHERE id=?", source);
    assertThat(say("Everyday").get("workflow").get("status").asText()).isEqualTo("REVIEW");
  }

  @Test
  void concurrentBillChatConfirmationsPostOnePaymentAndOneJournal() throws Exception {
    String bill = namedProduct("BILL", "Electricity");
    var review = say("pay electricity bill from Everyday").get("workflow");
    String id = review.get("id").asText();
    assertThat(review.get("message").asText()).contains("Chat Customer", "Confirming transfers money");
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var first = pool.submit(() -> command(id, "CONFIRM", "", UUID.randomUUID().toString()));
      var second = pool.submit(() -> command(id, "CONFIRM", "", UUID.randomUUID().toString()));
      String reference = first.get().get("workflow").get("reference").asText();
      assertThat(reference).startsWith("TX-");
      assertThat(second.get().get("workflow").get("reference").asText()).isEqualTo(reference);
      assertThat(db.queryForObject("SELECT COUNT(*) FROM transactions WHERE parent_id=? AND operation='BILL_PAYMENT'", Integer.class, bill)).isEqualTo(1);
      assertThat(db.queryForObject("SELECT COUNT(*) FROM journal_entries WHERE transaction_id=? AND status='POSTED'", Integer.class, reference)).isEqualTo(1);
      assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source)).isEqualTo(9900);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void billFailureAfterReviewIsRecordedWithoutSettlingTheBill() throws Exception {
    String bill = namedProduct("BILL", "Electricity");
    var review = say("pay electricity bill from Everyday").get("workflow");
    db.update("UPDATE accounts SET balance=0 WHERE id=?", source);
    var result = command(review.get("id").asText(), "CONFIRM", "", UUID.randomUUID().toString()).get("workflow");
    assertThat(result.get("status").asText()).isEqualTo("FAILED");
    assertThat(db.queryForObject("SELECT status FROM bill_payment_attempts WHERE id=?", String.class, review.get("reference").asText())).isEqualTo("FAILED");
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, destination)).isZero();
    mvc.perform(get("/api/v1/bills/" + bill).header("Authorization", auth))
        .andExpect(status().isOk()).andExpect(jsonPath("$.outstandingAmount").value("100.00"));
  }

  @Test
  void oldCardSimulationRequestsCannotPretendToChangeCards() throws Exception {
    String target = demoProduct("CARD");
    String before = productSnapshot("CARD", target);
    String owner = db.queryForObject("SELECT c.user_id FROM customers c JOIN accounts a ON a.customer_id=c.id WHERE a.id=?", String.class, source);
    for (String operation : List.of("FREEZE_CARD", "UNFREEZE_CARD", "REPLACE_CARD")) {
      postJson("/api/v1/demo/actions/prepare",
          Map.of("operation", operation, "targetId", target), 400);
      String id = UUID.randomUUID().toString();
      db.update("INSERT INTO transactions (record_kind,id,user_id,operation,source_account_id,target_id,status,expires_at) VALUES ('SIMULATION',?,?,?,?,?,'REVIEW',?)",
          id, owner, operation, source, target, java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(600)));
      postJson("/api/v1/demo/actions/" + id + "/confirm", Map.of(), 400);
    }
    assertThat(productSnapshot("CARD", target)).isEqualTo(before);
  }

  @Test
  void providerSimulationsHaveDurableReceiptsWithoutProductOrLedgerWrites() throws Exception {
    for (String operation :
        List.of(
            "PAY_CARD",
            "CANCEL_MANDATE")) {
      String kind = operation.equals("CANCEL_MANDATE") ? "MANDATE" : "CARD";
      String target = demoProduct(kind);
      String before = productSnapshot(kind, target);
      var review =
          postJson(
              "/api/v1/demo/actions/prepare",
              Map.of(
                  "operation", operation, "accountId", source, "targetId", target, "amount", "100"),
              200);
      String path = "/api/v1/demo/actions/" + review.get("id").asText();
      assertThat(review.get("status").asText()).isEqualTo("REVIEW");
      var receipt = postJson(path + "/confirm", Map.of(), 200);
      assertThat(receipt.get("status").asText()).isEqualTo("SIMULATED");
      assertThat(receipt.get("simulated").asBoolean()).isTrue();
      assertThat(postJson(path + "/confirm", Map.of(), 200)).isEqualTo(receipt);
      mvc.perform(get(path).header("Authorization", auth))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.reference").value(receipt.get("reference").asText()));
      assertThat(productSnapshot(kind, target)).isEqualTo(before);
    }
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source))
        .isEqualTo(10000);
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE record_kind='PAYMENT' AND"
                    + " source_account_id=?",
                Integer.class,
                source))
        .isZero();
  }

  @Test
  void demoReviewsEnforceOwnershipExpiryCancellationAndRevalidation() throws Exception {
    String target = demoProduct("CARD");
    var request =
        Map.of("operation", "PAY_CARD", "accountId", source, "targetId", target, "amount", "100");
    String id = postJson("/api/v1/demo/actions/prepare", request, 200).get("id").asText();
    String path = "/api/v1/demo/actions/" + id;
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    String ownerAuth = auth;
    auth =
        "Bearer "
            + postJson(
                    "/api/v1/auth/register",
                    Map.of(
                        "fullName",
                        "Other demo user",
                        "email",
                        UUID.randomUUID() + "@example.com",
                        "password",
                        "SecureTest@123"),
                    201)
                .get("accessToken")
                .asText();
    mvc.perform(get(path).header("Authorization", auth)).andExpect(status().isNotFound());
    postJson(path + "/confirm", Map.of(), 404);
    postJson(path + "/cancel", Map.of(), 404);
    postJson("/api/v1/demo/actions/prepare", request, 404);
    auth = ownerAuth;
    postJson(path + "/cancel", Map.of(), 200);
    postJson(path + "/confirm", Map.of(), 400);
    id = postJson("/api/v1/demo/actions/prepare", request, 200).get("id").asText();
    db.update(
        "UPDATE transactions SET expires_at=? WHERE id=?",
        java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),
        id);
    postJson("/api/v1/demo/actions/" + id + "/confirm", Map.of(), 400);
    id = postJson("/api/v1/demo/actions/prepare", request, 200).get("id").asText();
    db.update("UPDATE accounts SET balance=0 WHERE id=?", source);
    postJson("/api/v1/demo/actions/" + id + "/confirm", Map.of(), 400);
    postJson("/api/v1/demo/actions/prepare", request, 400);
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
  void ambiguousBeneficiariesRequireSelectionAndPostRealPayment() throws Exception {
    String owner =
        db.queryForObject(
            "SELECT c.user_id FROM customers c JOIN accounts a ON a.customer_id=c.id WHERE a.id=?",
            String.class,
            source);
    for (String suffix : List.of("Sharma", "Verma")) {
      db.update(
          "INSERT INTO"
              + " transactions(id,user_id,display_name,beneficiary_type,recipient_name,destination_masked,destination_hash,status,created_at,updated_at,record_kind,bank_name)"
              + " VALUES"
              + " (?,?,?,'BANK_ACCOUNT',?,'1234',?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'BENEFICIARY','Nexa')",
          "ben_" + suffix,
          owner,
          "Rahul " + suffix,
          "Rahul " + suffix,
          "0".repeat(64));
    }
    db.update(
        "UPDATE transactions SET destination_account_id=? WHERE id IN ('ben_Sharma','ben_Verma')",
        destination);
    var start = say("Transfer 5000 to Rahul");
    String id = start.get("workflow").get("id").asText();
    assertThat(start.get("workflow").get("choices").size()).isEqualTo(2);
    assertThat(start.get("workflow").get("message").asText()).contains("more than one");
    command(id, "SELECT", "ben_Sharma", UUID.randomUUID().toString());
    var review = command(id, "SELECT", source, UUID.randomUUID().toString());
    assertThat(review.get("workflow").get("status").asText()).isEqualTo("REVIEW");
    var completed = command(id, "CONFIRM", "", UUID.randomUUID().toString());
    assertThat(completed.get("workflow").get("reference").asText()).startsWith("TX-");
    var replay = command(id, "CONFIRM", "", UUID.randomUUID().toString());
    assertThat(replay.get("workflow")).isEqualTo(completed.get("workflow"));
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source))
        .isEqualTo(5000);
    assertThat(
            db.queryForObject(
                "SELECT balance FROM accounts WHERE id=?", Integer.class, destination))
        .isEqualTo(5000);
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

  @Test
  void chatLoanRepaymentRequiresConfirmationAndPostsExactlyOnce() throws Exception {
    var loan =
        postJson(
            "/api/v1/loans",
            Map.of(
                "accountId",
                Long.valueOf(source),
                "displayName",
                "Travel loan",
                "principal",
                "200",
                "interestRate",
                "5"),
            200);
    String loanId = loan.get("PRODUCT_ID").asText();
    com.nexa.api.banking.LoanTestSupport.approvedFixture(db,loanId);
    postJson("/api/v1/loans/" + loanId + "/disburse", Map.of(), 200);
    var review = say("repay 40 to Travel loan").get("workflow");
    assertThat(review.get("operation").asText()).isEqualTo("REPAY_LOAN");
    assertThat(review.get("status").asText()).isEqualTo("REVIEW");
    assertThat(
            db.queryForObject(
                "SELECT balance FROM accounts WHERE product_id=?", Integer.class, loanId))
        .isEqualTo(200);
    assertThat(say("yes").get("workflow").get("status").asText()).isEqualTo("REVIEW");
    String action = review.get("id").asText();
    var result = command(action, "CONFIRM", "", UUID.randomUUID().toString());
    assertThat(result.get("workflow").get("reference").asText()).startsWith("TX-");
    assertThat(
            db.queryForObject(
                "SELECT balance FROM accounts WHERE product_id=?", Integer.class, loanId))
        .isEqualTo(160);
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source))
        .isEqualTo(10160);
    assertThat(command(action, "CONFIRM", "", UUID.randomUUID().toString()).get("workflow"))
        .isEqualTo(result.get("workflow"));
  }

  @Test
  void unlinkedPayeeReturnsAnExplanationInsteadOfPretendingToTransfer() throws Exception {
    String id = payee("Unlinked recipient");
    db.update("UPDATE transactions SET destination_account_id=NULL WHERE id=?", id);
    var result = say("transfer 40 to Unlinked recipient from Everyday");
    assertThat(result.get("workflow").get("status").asText()).isNotEqualTo("COMPLETED");
    assertThat(result.get("workflow").get("message").asText()).contains("verified Nexa account");
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source))
        .isEqualTo(10000);
  }

  @Test
  void chatScheduledLoanRoutesExtraPrincipalToTheExplicitChoiceScreen() throws Exception {
    if (db.queryForObject("SELECT COUNT(*) FROM accounts WHERE account_number='NEXA-LOAN-INTEREST'", Integer.class) == 0)
      db.update("INSERT INTO accounts(account_number,account_name,account_type,account_category,currency_code,balance,status,version,created_at,updated_at)"
          + " VALUES('NEXA-LOAN-INTEREST','Loan interest','CLEARING','SYSTEM','INR',0,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    String id = postJson("/api/v1/loans", Map.of("accountId", Long.valueOf(source),
        "applicationKey", UUID.randomUUID().toString(), "purpose", "Education loan",
        "amount", "12000", "tenureMonths", 6), 201).get("id").asText();
    com.nexa.api.banking.LoanTestSupport.approvedFixture(db, id);
    postJson("/api/v1/loans/" + id + "/accept", Map.of(), 200);
    var review = say("repay 5000 to Education loan").get("workflow");
    assertThat(review.get("status").asText()).isNotEqualTo("REVIEW");
    assertThat(review.get("message").asText()).contains("compare reduce-tenure or reduce-EMI");
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE product_id=?",
        java.math.BigDecimal.class, id)).isEqualByComparingTo("12000");
    var topup = say("repay 50 to Education loan").get("workflow");
    assertThat(topup.get("status").asText()).isNotEqualTo("REVIEW");
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE product_id=?",
        java.math.BigDecimal.class, id)).isEqualByComparingTo("12000");
  }

  @Test
  void chatScheduledLoanUsesTheFullEmiIncludingFinalInterest() throws Exception {
    if (db.queryForObject("SELECT COUNT(*) FROM accounts WHERE account_number='NEXA-LOAN-INTEREST'", Integer.class) == 0)
      db.update("INSERT INTO accounts(account_number,account_name,account_type,account_category,currency_code,balance,status,version,created_at,updated_at)"
          + " VALUES('NEXA-LOAN-INTEREST','Loan interest income','CLEARING','SYSTEM','INR',0,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    var loan = postJson("/api/v1/loans", Map.of("accountId", Long.valueOf(source),
        "applicationKey", "chat-" + UUID.randomUUID(), "purpose", "Study loan", "amount", "12000", "tenureMonths", 1), 201);
    String loanId = loan.get("id").asText();
    com.nexa.api.banking.LoanTestSupport.approvedFixture(db,loanId);
    postJson("/api/v1/loans/" + loanId + "/accept", Map.of(), 200);
    var review = say("repay Study loan").get("workflow");
    assertThat(review.get("status").asText()).isEqualTo("REVIEW");
    assertThat(new java.math.BigDecimal(review.get("amount").asText())).isEqualByComparingTo("12145");
    String action = review.get("id").asText();
    var result = command(action, "CONFIRM", "", UUID.randomUUID().toString());
    assertThat(result.get("workflow").get("status").asText()).isEqualTo("COMPLETED");
    assertThat(db.queryForObject("SELECT product_status FROM accounts WHERE product_id=?", String.class, loanId)).isEqualTo("CLOSED");
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", java.math.BigDecimal.class, source)).isEqualByComparingTo("9855");
    assertThat(command(action, "CONFIRM", "", UUID.randomUUID().toString()).get("workflow")).isEqualTo(result.get("workflow"));
  }
}
