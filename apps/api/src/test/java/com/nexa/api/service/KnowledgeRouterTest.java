package com.nexa.api.service;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class KnowledgeRouterTest {
  KnowledgeBase kb;
  KnowledgeRouter router;

  @BeforeEach
  void setup() throws Exception {
    kb =
        new KnowledgeBase(
            tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build());
    router = new KnowledgeRouter(kb, new LoanCalculationService(new BigDecimal("9.25")));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "What is EMI?",
        "What is a scheduled payment?",
        "How do I freeze my card?",
        "Can I open a savings account?",
        "How do I add a payee?",
        "Does Nexa support Hindi?",
        "Can I use voice?",
        "What does freezing a card do?",
        "What is available balance?"
      })
  void knowledgeNeverRequestsExecution(String question) {
    var result = router.route(question, null);
    assertThat(result.answer()).isNotNull();
    assertThat(result.answer().banking().meta()).containsKey("knowledgeId");
    assertThat(result.category())
        .isNotIn(KnowledgeRouter.Category.TRANSACTION, KnowledgeRouter.Category.ACCOUNT_DATA);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "What is my balance?",
        "Show my accounts",
        "What scheduled payments do I have?",
        "What is my next EMI?",
        "Is my card frozen?",
        "How much did I spend on food last month?",
        "Did my salary arrive?",
        "What is my account number?"
      })
  void personalQuestionsGoToAuthenticatedData(String question) {
    var result = router.route(question, "loan");
    assertThat(result.category()).isEqualTo(KnowledgeRouter.Category.ACCOUNT_DATA);
    assertThat(result.answer()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Transfer 5000 to Rahul",
        "Freeze my card",
        "Unfreeze my card",
        "Replace my card",
        "Pay my electricity bill",
        "Make my EMI payment",
        "Pay my next EMI"
      })
  void supportedActionsReachWorkflows(String question) {
    var result = router.route(question, "loan");
    assertThat(result.category()).isEqualTo(KnowledgeRouter.Category.TRANSACTION);
    assertThat(result.answer()).isNull();
  }

  @Test
  void contextUsesConfiguredRateAndDocuments() {
    var first = router.route("What is a personal loan?", null).answer();
    String topic = first.banking().meta().get("knowledgeTopic").toString();
    assertThat(router.route("What is the interest rate?", topic).answer().reply())
        .contains("9.25%");
    assertThat(router.route("What documents do I need?", topic).answer().reply())
        .contains("three months");
    assertThat(router.route("What is a savings account?", topic).answer().banking().meta())
        .containsEntry("knowledgeTopic", "savings");
    assertThat(router.route("What is the interest rate?", "savings").answer().banking().errorCode())
        .isEqualTo("KNOWLEDGE_UNAVAILABLE");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "What are Nexa's transfer limits?",
        "What are Nexa loan fees?",
        "What is the home loan rate?",
        "What are loan eligibility requirements?",
        "What is the savings account rate?"
      })
  void unknownTermsNeverUseGeneralKnowledge(String text) {
    assertThat(router.route(text, null).answer().banking().errorCode())
        .isEqualTo("KNOWLEDGE_UNAVAILABLE");
  }

  @Test
  void unknownCapabilitiesAndUnsupportedSchedulingDoNotPromiseExecution() {
    assertThat(
            router.route("Does Nexa support cryptocurrency?", null).answer().banking().errorCode())
        .isEqualTo("KNOWLEDGE_UNAVAILABLE");
    assertThat(router.route("Schedule 2000 to Amit every month", null).answer().reply())
        .contains("not currently available", "No payment was created");
    assertThat(router.route("Can I transfer money between my accounts?", null).answer().reply())
        .contains("own accounts", "explicitly confirm");
  }

  @Test
  void localizedAndSimulatedResponses() {
    assertThat(router.route("EMI kya hota hai?", null).answer().reply()).contains("kist");
    assertThat(router.route("ईएमआई क्या है?", null).answer().reply()).contains("किस्त");
    assertThat(router.route("How do I freeze my card?", null).answer().reply())
        .contains("demonstrations", "not a real network");
    assertThat(router.route("How do Nexa bill payments work?", null).answer().reply())
        .contains("internal banking", "does not contact");
  }

  @Test
  void explicitLoanRateUsesProductConfigurationAndQuoteUsesCalculator() {
    assertThat(router.route("What is Nexa's loan interest rate?", null).answer().reply())
        .contains("9.25%");
    assertThat(router.route("Calculate EMI for INR 10000 over 12 months", null).answer().reply())
        .contains("9.25%", "not an approval", "Total scheduled interest");
    assertThat(
            router
                .route("Calculate EMI for INR 10000 at 5% over 12 months", null)
                .answer()
                .banking()
                .errorCode())
        .isEqualTo("QUOTE_DETAILS_REQUIRED");
    assertThat(
            router
                .route("Calculate EMI for 10000 USD over 12 months", null)
                .answer()
                .banking()
                .errorCode())
        .isEqualTo("QUOTE_DETAILS_REQUIRED");
    var expected =
        new LoanCalculationService(new BigDecimal("9.25"))
            .quote(new com.nexa.api.beans.LoanModels.QuoteRequest(new BigDecimal("10000.50"), 12));
    assertThat(
            router.route("Calculate EMI for INR 10,000.50 over 12 months", null).answer().reply())
        .contains(expected.emiAmount().toPlainString(), expected.totalInterest().toPlainString());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"balance batao", "only debit transactions", "change account", "Everyday", "yes"})
  void fragmentsRemainAvailableToExistingConversationContext(String text) {
    assertThat(router.route(text, null).answer()).isNull();
  }

  @Test
  void administratorContentRequiresStaffRoleAndCannotExecute() {
    assertThat(router.route("What can an administrator do?", null).answer().banking().errorCode())
        .isEqualTo("FORBIDDEN");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "staff", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    assertThat(router.route("What can an administrator do?", null).answer().reply())
        .contains("Authorized staff");
    assertThat(router.route("Approve this loan", null).answer().reply())
        .contains("No administrative action");
  }

  @Test
  void retrievalPrefersActiveLatestNexaVersion() {
    var today = LocalDate.now();
    var entries =
        List.of(
            entry(1, today.minusDays(3), true, "NEXA"),
            entry(2, today.minusDays(1), true, "NEXA"),
            entry(3, today, false, "NEXA"),
            entry(4, today.plusDays(1), true, "NEXA"),
            entry(5, today, true, "GENERAL"));
    var catalog = new KnowledgeBase(entries);
    assertThat(catalog.find("loan", "RATE", true, false).orElseThrow().version()).isEqualTo(2);
    assertThat(catalog.find("loan", "RATE", false, false).orElseThrow().version()).isEqualTo(2);
  }

  private KnowledgeBase.Entry entry(int version, LocalDate date, boolean active, String scope) {
    return new KnowledgeBase.Entry(
        "loan-rate",
        "loan",
        "RATE",
        scope,
        version,
        date,
        null,
        active,
        "CUSTOMER",
        List.of("loan"),
        "Verified test content",
        null,
        null,
        "test");
  }
}
