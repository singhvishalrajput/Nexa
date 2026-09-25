package com.nexa.api.conversations;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nexa.api.service.Workflow;
import db.migration.V17__migrate_banking_products;
import db.migration.V25__assign_full_card_numbers;
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
  @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
  String auth, chat, source, destination;

  @Test
  void sensitiveNumbersAreOwnerScopedUncachedAndCardNumbersPersist() throws Exception {
    String full = db.queryForObject("SELECT account_number FROM accounts WHERE id=?", String.class, source);
    mvc.perform(get("/api/v1/sensitive-numbers/accounts/" + source).header("Authorization", auth))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.number").value(full));
    mvc.perform(get("/api/v1/sensitive-numbers/accounts/" + source)).andExpect(status().isUnauthorized());
    String card = demoProduct("CARD");
    db.update("UPDATE accounts SET number_masked='•••• 6012' WHERE product_id=?", card);
    V25__assign_full_card_numbers.backfill(db);
    String cardNumber = db.queryForObject("SELECT account_number FROM accounts WHERE product_id=?", String.class, card);
    assertThat(cardNumber).matches("[0-9]{16}").endsWith("6012");
    V25__assign_full_card_numbers.backfill(db);
    assertThat(db.queryForObject("SELECT account_number FROM accounts WHERE product_id=?", String.class, card)).isEqualTo(cardNumber);
    mvc.perform(get("/api/v1/sensitive-numbers/cards/" + card).header("Authorization", auth))
        .andExpect(status().isOk()).andExpect(jsonPath("$.number").value(cardNumber));
    mvc.perform(get("/api/v1/sensitive-numbers/accounts/999999999").header("Authorization", auth))
        .andExpect(status().isNotFound());
    var other = postJson("/api/v1/auth/register", Map.of("fullName", "Other customer", "email", UUID.randomUUID() + "@example.com", "password", "SecureTest@123"), 201);
    auth = "Bearer " + other.get("accessToken").asText();
    mvc.perform(get("/api/v1/sensitive-numbers/accounts/" + source).header("Authorization", auth))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/sensitive-numbers/cards/" + card).header("Authorization", auth))
        .andExpect(status().isNotFound());
  }

  @Test
  void bankSupportDetailsAreSharedByCoreBankingAndChat() throws Exception {
    mvc.perform(get("/api/v1/cards/support").header("Authorization", auth))
        .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("88000 00067"))
        .andExpect(jsonPath("$.email").value("nexa@help.bank.in"));
    var reply = say("create a credit card").get("assistantText").asText();
    assertThat(reply).contains("88000 00067", "nexa@help.bank.in");
  }

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
            new ClassPathResource("db/migration/V13__conversation_action_audit.sql"))
        .execute(db.getDataSource());
    db.execute("ALTER TABLE transactions ADD CONSTRAINT ck_tx_record_kind CHECK(record_kind IN ('PAYMENT','MANDATE','MANDATE_EVENT','BILL','SCHEDULED_PAYMENT','BENEFICIARY','TRANSFER_REVIEW','SIMULATION','LEGACY_TRANSFER','PRODUCT_HISTORY','ADMIN_EVENT','LOAN_INSTALLMENT'))");
    db.execute("ALTER TABLE transactions ADD CONSTRAINT ck_posting_accounts CHECK (record_kind <> 'PAYMENT' OR (transaction_type='DEPOSIT' AND source_account_id IS NULL AND destination_account_id IS NOT NULL) OR (transaction_type='WITHDRAWAL' AND source_account_id IS NOT NULL AND destination_account_id IS NULL) OR (transaction_type IN ('TRANSFER','LOAN_DISBURSEMENT','LOAN_REPAYMENT') AND source_account_id IS NOT NULL AND destination_account_id IS NOT NULL AND source_account_id<>destination_account_id))");
    new ResourceDatabasePopulator(new ClassPathResource("db/migration/V24__card_payment_reviews.sql")).execute(db.getDataSource());
    db.execute("ALTER TABLE transactions ADD CONSTRAINT fk_transaction_parent_test FOREIGN KEY(parent_id) REFERENCES transactions(id)");
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
        "0".repeat(64));
    db.update("UPDATE transactions SET destination_account_id=? WHERE id=?", destination, id);
    return id;
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
    assertThat(first.get("amount").asText()).isEqualTo("100");
    for (String followup : List.of("bill pay karo", "haan", "do it", "haan kar do")) {
      var continued = say(followup).get("workflow");
      assertThat(continued.get("id")).isEqualTo(first.get("id"));
      assertThat(continued.get("targetId").asText()).isEqualTo(electricity);
      assertThat(continued.get("message").asText()).contains("Electricity", "which account");
      assertThat(continued.get("amount").asText()).isEqualTo("100");
    }
    var review = say("Everyday se").get("workflow");
    assertThat(review.get("status").asText()).isEqualTo("REVIEW");
    for (String followup : List.of("yes", "pay karo", "confirm", "हाँ कर दो"))
      assertThat(say(followup).get("workflow").get("status").asText()).isEqualTo("REVIEW");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE status='SUCCESS' AND target_id=?",
                Integer.class,
                electricity))
        .isZero();
    var result = command(review.get("id").asText(), "CONFIRM", "", UUID.randomUUID().toString());
    assertThat(result.get("assistantText").asText())
        .contains("100", "Electricity", "posted")
        .doesNotContain("Open Payments", "COLLECTING");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE status='SUCCESS' AND target_id=? AND operation='BILL_PAYMENT'",
                Integer.class,
                electricity))
        .isEqualTo(1);
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
  void cardAndMandateActionsStayInConversation() throws Exception {
    String card = namedProduct("CARD", "Travel");
    namedProduct("MANDATE", "Music");
    for (String request :
        List.of(
            "pay Travel card from Everyday",
            "freeze Travel card",
            "unfreeze Travel card",
            "replace Travel card",
            "cancel Music mandate")) {
      var review = say(request).get("workflow");
      assertThat(review.get("status").asText()).as(request).isEqualTo("REVIEW");
      assertThat(say("haan kar do").get("workflow").get("id")).isEqualTo(review.get("id"));
      var result = command(review.get("id").asText(), "CONFIRM", "", UUID.randomUUID().toString());
      if (request.startsWith("pay "))
        assertThat(result.get("assistantText").asText()).contains("Credit-card payment posted");
      else {
        assertThat(result.get("assistantText").asText()).contains("accepted").doesNotContain("Open");
        assertThat(result.get("assistantText").asText()).contains("Simulation recorded");
      }
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
    if (db.queryForObject("SELECT COUNT(*) FROM accounts WHERE account_number='CHAT-CASH'", Integer.class) == 0)
      db.update("INSERT INTO accounts(account_number,account_name,account_type,account_category,currency_code,balance,status,version,created_at,updated_at)"
          + " VALUES('CHAT-CASH','Chat test cash','CASH','SYSTEM','INR',10000,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    chat = postJson("/api/v1/conversations", Map.of(), 201).get("id").asText();
  }

  @Test
  void percentageSurvivesSelectionsAndUsesCurrentPayableAmount() throws Exception {
    String bill = namedProduct("BILL", "Electricity");
    var start = say("Pay half my bill").get("workflow");
    assertThat(start.get("field").asText()).isEqualTo("target");
    String id = start.get("id").asText();
    var funding = command(id, "SELECT", bill, UUID.randomUUID().toString()).get("workflow");
    assertThat(funding.get("amount").asText()).isEqualTo("50.00");
    db.update("UPDATE transactions SET amount=80 WHERE id=?", bill);
    var review = command(id, "SELECT", source, UUID.randomUUID().toString()).get("workflow");
    assertThat(review.get("status").asText()).isEqualTo("REVIEW");
    assertThat(review.get("amount").asText()).isEqualTo("40.00");
    assertThat(review.get("message").asText()).contains("INR 40.00", "Electricity", "Everyday");
    say("yes");
    assertThat(db.queryForObject("SELECT amount FROM transactions WHERE id=?", Integer.class, bill)).isEqualTo(80);
    command(id, "CONFIRM", "", UUID.randomUUID().toString());
    assertThat(db.queryForObject("SELECT amount FROM transactions WHERE id=?", Integer.class, bill)).isEqualTo(40);
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source)).isEqualTo(9960);
  }

  @Test
  void percentageSupportsCreditFundingAndSavingsDisambiguation() throws Exception {
    String bill = namedProduct("BILL", "Broadband");
    String card = namedProduct("CARD", "Violet Credit");
    var savings = say("Pay 25% of my broadband bill from savings").get("workflow");
    assertThat(savings.get("field").asText()).isEqualTo("account");
    assertThat(savings.get("choices").size()).isEqualTo(2);
    var review = command(savings.get("id").asText(), "SELECT", source, UUID.randomUUID().toString()).get("workflow");
    assertThat(review.get("amount").asText()).isEqualTo("25.00");
    var credit = say("Pay 50% of my broadband bill using my credit card").get("workflow");
    assertThat(credit.get("operation").asText()).isEqualTo("PAY_BILL");
    assertThat(credit.get("status").asText()).isEqualTo("REVIEW");
    assertThat(credit.get("amount").asText()).isEqualTo("50.00");
    command(credit.get("id").asText(), "CONFIRM", "", UUID.randomUUID().toString());
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE product_id=?", Integer.class, card)).isEqualTo(550);
    assertThat(db.queryForObject("SELECT amount FROM transactions WHERE id=?", Integer.class, bill)).isEqualTo(50);
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source)).isEqualTo(10000);
  }

  @Test
  void invalidPercentagesAndBillLimitsCannotProduceAReview() throws Exception {
    String bill = namedProduct("BILL", "Electricity");
    db.update("UPDATE transactions SET minimum_amount=30 WHERE id=?", bill);
    for (String percentage : List.of("0%", "-10%", "101%", "abc%", "1.2.3%")) {
      var rejected = say("pay " + percentage + " of my electricity bill from Everyday").get("workflow");
      assertThat(rejected.get("status").asText()).isEqualTo("COLLECTING");
      assertThat(rejected.get("message").asText()).contains("valid percentage", "100%");
    }
    var tooSmall = say("pay a quarter of my electricity bill from Everyday").get("workflow");
    assertThat(tooSmall.get("status").asText()).isEqualTo("COLLECTING");
    assertThat(tooSmall.get("message").asText()).contains("25%", "INR 25.00", "outside the bill payment range");
    postJson("/api/v1/conversations/" + chat + "/actions/" + tooSmall.get("id").asText(),
        Map.of("clientId", UUID.randomUUID().toString(), "type", "CONFIRM"), 400);
    var fixed = say("50%").get("workflow");
    assertThat(fixed.get("amount").asText()).isEqualTo("50.00");
    assertThat(fixed.get("status").asText()).isEqualTo("REVIEW");
    var literal = say("make it 40").get("workflow");
    assertThat(literal.get("amount").asText()).isEqualTo("40");
    assertThat(literal.path("amountRequest").isNull()).isTrue();
  }

  @Test
  void cardMinimumAndFullShortcutsKeepExplicitConfirmation() throws Exception {
    String card = namedProduct("CARD", "Violet Credit");
    var start = say("Pay the minimum due on my credit card from savings").get("workflow");
    var selection = command(start.get("id").asText(), "SELECT", source, UUID.randomUUID().toString()).get("workflow");
    assertThat(selection.get("field").asText()).isEqualTo("target");
    var review = command(selection.get("id").asText(), "SELECT", card, UUID.randomUUID().toString()).get("workflow");
    assertThat(new java.math.BigDecimal(review.get("amount").asText())).isEqualByComparingTo("10");
    assertThat(review.get("status").asText()).isEqualTo("REVIEW");
    say("yes");
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE product_id=?", Integer.class, card)).isEqualTo(500);
    command(review.get("id").asText(), "CONFIRM", "", UUID.randomUUID().toString());
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE product_id=?", Integer.class, card)).isEqualTo(490);
    for (String shortcut : List.of("Pay my full credit-card outstanding", "Pay my credit card full due", "Clear my card bill", "Clear my bill")) {
      chat = postJson("/api/v1/conversations", Map.of(), 201).get("id").asText();
      var next = say(shortcut).get("workflow");
      assertThat(next.get("operation").asText()).isEqualTo("PAY_CARD");
      var account = command(next.get("id").asText(), "SELECT", card, UUID.randomUUID().toString()).get("workflow");
      var full = command(account.get("id").asText(), "SELECT", source, UUID.randomUUID().toString()).get("workflow");
      assertThat(full.get("status").asText()).isEqualTo("REVIEW");
      assertThat(new java.math.BigDecimal(full.get("amount").asText())).isEqualByComparingTo("490");
    }
  }

  @Test
  void percentageDisambiguationRoundingAndFundingValidationUseExistingRules() throws Exception {
    String north = namedProduct("BILL", "Electricity North");
    String south = namedProduct("BILL", "Electricity South");
    db.update("UPDATE transactions SET amount=100.01 WHERE id=?", north);
    var ambiguous = say("pay half my electricity bill from Everyday").get("workflow");
    assertThat(ambiguous.get("field").asText()).isEqualTo("target");
    assertThat(ambiguous.get("choices").size()).isEqualTo(2);
    var rounded = command(ambiguous.get("id").asText(), "SELECT", north, UUID.randomUUID().toString()).get("workflow");
    assertThat(rounded.get("amount").asText()).isEqualTo("50.01");
    var changed = say("Electricity South instead").get("workflow");
    assertThat(changed.get("targetId").asText()).isEqualTo(south);
    assertThat(changed.get("amount").asText()).isEqualTo("50.00");
    postJson("/api/v1/conversations/" + chat + "/actions/" + rounded.get("id").asText(),
        Map.of("clientId", UUID.randomUUID().toString(), "type", "CONFIRM"), 400);
    db.update("UPDATE accounts SET balance=20 WHERE id=?", source);
    var insufficient = say("100%").get("workflow");
    assertThat(insufficient.get("status").asText()).isEqualTo("COLLECTING");
    assertThat(insufficient.get("message").asText()).contains("available balance is insufficient");
    db.update("UPDATE accounts SET balance=10000 WHERE id=?", source);
    String debit = namedProduct("CARD", "Digital Debit");
    db.update("UPDATE accounts SET product_type='DEBIT' WHERE product_id=?", debit);
    var ineligible = say("pay half my electricity south bill using Digital Debit").get("workflow");
    assertThat(ineligible.get("status").asText()).isEqualTo("COLLECTING");
    assertThat(ineligible.get("field").asText()).isEqualTo("account");
    assertThat(ineligible.get("choices").toString()).doesNotContain("Digital Debit");
    var unknown = say("pay half my missing broadband bill from Everyday").get("workflow");
    assertThat(unknown.get("field").asText()).isEqualTo("target");
    assertThat(unknown.get("targetId").isNull()).isTrue();
    var selected = command(unknown.get("id").asText(), "SELECT", north, UUID.randomUUID().toString()).get("workflow");
    assertThat(selected.get("amount").asText()).isEqualTo("50.01");
  }

  @Test
  void cardShortcutRejectsCardFundingAndRecalculatesAfterTargetChange() throws Exception {
    String violet = namedProduct("CARD", "Violet Credit");
    String amber = namedProduct("CARD", "Amber Credit");
    db.update("UPDATE accounts SET minimum_payment=35 WHERE product_id=?", amber);
    var ambiguous = say("pay minimum due on my credit card from Everyday").get("workflow");
    assertThat(ambiguous.get("field").asText()).isEqualTo("target");
    assertThat(ambiguous.get("choices").size()).isEqualTo(2);
    var first = command(ambiguous.get("id").asText(), "SELECT", violet, UUID.randomUUID().toString()).get("workflow");
    assertThat(new java.math.BigDecimal(first.get("amount").asText())).isEqualByComparingTo("10");
    var changed = say("Amber instead").get("workflow");
    assertThat(new java.math.BigDecimal(changed.get("amount").asText())).isEqualByComparingTo("35");
    var rejected = say("use my credit card").get("workflow");
    assertThat(rejected.get("status").asText()).isEqualTo("COLLECTING");
    assertThat(rejected.get("message").asText()).contains("cannot be paid using another credit card");
    assertThat(rejected.get("choices").size()).isEqualTo(2);
    var repaired = say("Everyday").get("workflow");
    var full = say("full outstanding").get("workflow");
    assertThat(repaired.get("status").asText()).isEqualTo("REVIEW");
    assertThat(new java.math.BigDecimal(full.get("amount").asText())).isEqualByComparingTo("500");
  }

  @Test
  void lookupFindsOwnedBillsAndPayeesPastOneHundredAndDisambiguates() throws Exception {
    String template = namedProduct("BILL", "Other bill");
    String owner = db.queryForObject("SELECT user_id FROM transactions WHERE id=?", String.class, template);
    for (int i = 0; i < 110; i++) {
      db.update("INSERT INTO transactions(id,record_kind,user_id,display_name,status,amount,currency_code,created_at,updated_at) VALUES(?,'BILL',?,'Other bill','DUE',100,'INR',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", "a-" + UUID.randomUUID(), owner);
      payee("A filler " + i);
    }
    String bill = "zz-" + UUID.randomUUID();
    db.update("INSERT INTO transactions(id,record_kind,user_id,display_name,category,status,amount,currency_code,created_at,updated_at) VALUES(?,'BILL',?,'City utility','ELECTRICITY','DUE',100,'INR',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", bill, owner);
    String ravi = payee("Ravi Sharma");
    assertThat(say("show bills").get("banking").get("bills").size()).isEqualTo(112);
    assertThat(say("show beneficiaries").get("banking").get("beneficiaries").size()).isEqualTo(111);
    var payment = say("Pay half my ELECTRICITY bill from Everyday").get("workflow");
    assertThat(payment.get("targetId").asText()).isEqualTo(bill);
    assertThat(payment.get("amount").asText()).isEqualTo("50.00");
    var transfer = say("send 20 to rAvI from Everyday").get("workflow");
    assertThat(transfer.get("targetId").asText()).isEqualTo(ravi);
    payee("Ravi Kumar");
    var ambiguous = say("send 20 to Ravi from Everyday").get("workflow");
    assertThat(ambiguous.get("field").asText()).isEqualTo("target");
    assertThat(ambiguous.get("choices").size()).isEqualTo(2);
    assertThat(say("Ravi Kumar").get("workflow").get("status").asText()).isEqualTo("REVIEW");
    db.update("UPDATE transactions SET user_id='another-owner' WHERE id IN (?,?)", bill, ravi);
    chat = postJson("/api/v1/conversations", Map.of(), 201).get("id").asText();
    var hidden = say("pay my electricity bill from Everyday").get("workflow");
    assertThat(hidden.get("targetId").isNull()).isTrue();
    assertThat(hidden.get("choices").toString()).doesNotContain(bill);
    var hiddenPayee = say("send 20 to Ravi Sharma from Everyday").get("workflow");
    assertThat(hiddenPayee.toString()).doesNotContain(ravi);
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
    return id;
  }


  @Test
  void cardRepaymentPostsOnceFromCoreButtonsAndChat() throws Exception {
    String card = namedProduct("CARD", "Violet Credit");
    var review = postJson("/api/v1/demo/actions/prepare", Map.of("operation", "PAY_CARD", "accountId", source, "targetId", card, "amount", "100"), 200);
    assertThat(review.get("simulated").asBoolean()).isFalse();
    String path = "/api/v1/demo/actions/" + review.get("id").asText() + "/confirm";
    var receipt = postJson(path, Map.of(), 200);
    assertThat(receipt.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(postJson(path, Map.of(), 200)).isEqualTo(receipt);
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE product_id=?", Integer.class, card)).isEqualTo(400);
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source)).isEqualTo(9900);
    assertThat(db.queryForObject("SELECT parent_id FROM transactions WHERE id=?", String.class, receipt.get("reference").asText())).isNull();
    assertThat(db.queryForObject("SELECT target_id FROM transactions WHERE id=?", String.class, receipt.get("reference").asText())).isEqualTo(card);
    var workflow = say("pay credit card").get("workflow");
    String id = workflow.get("id").asText();
    command(id, "SELECT", card, UUID.randomUUID().toString());
    command(id, "SELECT", source, UUID.randomUUID().toString());
    var paid = command(id, "CONFIRM", "", UUID.randomUUID().toString());
    assertThat(paid.get("workflow").get("message").asText()).contains("Credit-card payment posted");
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE product_id=?", Integer.class, card)).isZero();
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source)).isEqualTo(9500);
    assertThat(db.queryForObject("SELECT SUM(CASE WHEN l.entry_type='DEBIT' THEN l.amount ELSE -l.amount END) FROM ledger_entries l JOIN journal_entries j ON j.id=l.journal_entry_id JOIN transactions t ON t.id=j.transaction_id WHERE t.target_id=?", java.math.BigDecimal.class, card)).isEqualByComparingTo("0");
    mvc.perform(get("/api/v1/cards/" + card + "/transactions").header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void billCanBeChargedToCreditCardWithoutDebitingDepositAccount() throws Exception {
    String card = namedProduct("CARD", "Violet Credit");
    String bill = namedProduct("BILL", "Electricity");
    String funding = db.queryForObject("SELECT id FROM accounts WHERE product_id=?", String.class, card);
    var review = postJson("/api/v1/demo/actions/prepare", Map.of("operation", "PAY_BILL", "accountId", funding, "targetId", bill, "amount", "100"), 200);
    String path = "/api/v1/demo/actions/" + review.get("id").asText() + "/confirm";
    var receipt = postJson(path, Map.of(), 200);
    assertThat(postJson(path, Map.of(), 200)).isEqualTo(receipt);
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE product_id=?", Integer.class, card)).isEqualTo(600);
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source)).isEqualTo(10000);
    assertThat(db.queryForObject("SELECT status FROM transactions WHERE id=?", String.class, bill)).isEqualTo("PAID");
    String nextBill = namedProduct("BILL", "Internet");
    var workflow = say("pay Internet bill using Violet Credit card").get("workflow");
    if (workflow.get("status").asText().equals("COLLECTING")) {
      String choice = workflow.get("field").asText().equals("target") ? nextBill : funding;
      workflow = command(workflow.get("id").asText(), "SELECT", choice, UUID.randomUUID().toString()).get("workflow");
    }
    assertThat(workflow.get("operation").asText()).isEqualTo("PAY_BILL");
    assertThat(workflow.get("accountId").asText()).isEqualTo(funding);
    command(workflow.get("id").asText(), "CONFIRM", "", UUID.randomUUID().toString());
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE product_id=?", Integer.class, card)).isEqualTo(700);
  }

  @Test
  void cardPaymentsRejectOverpaymentDebitCardsAndInsufficientCredit() throws Exception {
    String card = demoProduct("CARD"), bill = demoProduct("BILL");
    postJson("/api/v1/demo/actions/prepare", Map.of("operation", "PAY_CARD", "accountId", source, "targetId", card, "amount", "501"), 400);
    String funding = db.queryForObject("SELECT id FROM accounts WHERE product_id=?", String.class, card);
    db.update("UPDATE accounts SET balance=990 WHERE product_id=?", card);
    postJson("/api/v1/demo/actions/prepare", Map.of("operation", "PAY_BILL", "accountId", funding, "targetId", bill, "amount", "100"), 400);
    db.update("UPDATE accounts SET product_type='DEBIT' WHERE product_id=?", card);
    postJson("/api/v1/demo/actions/prepare", Map.of("operation", "PAY_CARD", "accountId", source, "targetId", card, "amount", "10"), 400);
    postJson("/api/v1/demo/actions/prepare", Map.of("operation", "PAY_BILL", "accountId", funding, "targetId", bill, "amount", "10"), 400);
  }

  @Test
  void cardConfirmationRechecksChangedBalanceAndOwnership() throws Exception {
    String card = demoProduct("CARD");
    var request = Map.of("operation", "PAY_CARD", "accountId", source, "targetId", card, "amount", "400");
    String first = postJson("/api/v1/demo/actions/prepare", request, 200).get("id").asText();
    String second = postJson("/api/v1/demo/actions/prepare", request, 200).get("id").asText();
    postJson("/api/v1/demo/actions/" + first + "/confirm", Map.of(), 200);
    postJson("/api/v1/demo/actions/" + second + "/confirm", Map.of(), 400);
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE product_id=?", Integer.class, card)).isEqualTo(100);
    var other = postJson("/api/v1/auth/register", Map.of("fullName", "Other card owner", "email", UUID.randomUUID() + "@example.com", "password", "SecureTest@123"), 201);
    auth = "Bearer " + other.get("accessToken").asText();
    postJson("/api/v1/cards/digital-debit", Map.of("accountId", source), 404);
    postJson("/api/v1/demo/actions/prepare", request, 404);
    postJson("/api/v1/demo/actions/" + first + "/confirm", Map.of(), 404);
  }

  @Test
  void partialBillPaymentsPreserveOutstandingAndHistory() throws Exception {
    String bill = demoProduct("BILL");
    var review = postJson("/api/v1/demo/actions/prepare", Map.of("operation", "PAY_BILL", "accountId", source, "targetId", bill, "amount", "40"), 200);
    postJson("/api/v1/demo/actions/" + review.get("id").asText() + "/confirm", Map.of(), 200);
    assertThat(db.queryForObject("SELECT amount FROM transactions WHERE id=?", Integer.class, bill)).isEqualTo(60);
    assertThat(db.queryForObject("SELECT status FROM transactions WHERE id=?", String.class, bill)).isEqualTo("DUE");
    mvc.perform(get("/api/v1/bills/" + bill).header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.paymentHistory.length()").value(1));
  }

  @Test
  void oldSimulatedCardReviewCannotBecomeARealPayment() throws Exception {
    String card = demoProduct("CARD");
    var review = postJson("/api/v1/demo/actions/prepare", Map.of("operation", "PAY_CARD", "accountId", source, "targetId", card, "amount", "100"), 200);
    String id = review.get("id").asText();
    db.update("UPDATE transactions SET record_kind='SIMULATION' WHERE id=?", id);
    postJson("/api/v1/demo/actions/" + id + "/confirm", Map.of(), 400);
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE product_id=?", Integer.class, card)).isEqualTo(500);
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", Integer.class, source)).isEqualTo(10000);
  }

  @Test
  void cardDiscoveryAndDigitalDebitIssuanceAreOwnedAndRepeatSafe() throws Exception {
    var empty = say("show cards linked to my account");
    assertThat(empty.get("banking").get("cards").size()).isZero();
    assertThat(empty.get("assistantText").asText()).contains("digital debit card");
    var credit = say("create a credit card");
    assertThat(credit.get("assistantText").asText()).contains("issuance", "support");
    var card = postJson("/api/v1/cards/digital-debit", Map.of("accountId", source), 200);
    assertThat(card.get("cardType").asText()).isEqualTo("DEBIT");
    String fullCardNumber = db.queryForObject("SELECT account_number FROM accounts WHERE product_id=?", String.class, card.get("id").asText());
    assertThat(fullCardNumber).matches("[0-9]{16}").endsWith(card.get("numberMasked").asText().substring(5));
    mvc.perform(get("/api/v1/sensitive-numbers/cards/" + card.get("id").asText()).header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.number").value(fullCardNumber));
    assertThat(postJson("/api/v1/cards/digital-debit", Map.of("accountId", source), 200).get("id")).isEqualTo(card.get("id"));
    namedProduct("CARD", "Violet Credit");
    assertThat(say("show cards linked to my account").get("banking").get("cards").size()).isEqualTo(2);
    postJson("/api/v1/cards/digital-debit", Map.of("accountId", destination), 400);
    mvc.perform(post("/api/v1/cards/digital-debit").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isUnauthorized());
  }

  @Test
  void providerSimulationsHaveDurableReceiptsWithoutProductOrLedgerWrites() throws Exception {
    for (String operation :
        List.of(
            "CANCEL_MANDATE",
            "FREEZE_CARD",
            "UNFREEZE_CARD",
            "REPLACE_CARD")) {
      String kind =
          operation.equals("PAY_BILL")
              ? "BILL"
              : operation.equals("CANCEL_MANDATE") ? "MANDATE" : "CARD";
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
    String target = demoProduct("BILL");
    var request =
        Map.of("operation", "PAY_BILL", "accountId", source, "targetId", target, "amount", "100");
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
