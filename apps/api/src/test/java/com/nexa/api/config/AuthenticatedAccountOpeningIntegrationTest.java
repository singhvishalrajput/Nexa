package com.nexa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexa.api.NexaApiApplication;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.web.MockPart;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Real registration/login/JWT filters/controllers/workflow/JDBC/JPA/identifier AES and HMAC.
 * All data is synthetic in isolated H2; no document store, scanner or live database is accessed. The H2
 * initializer omits Oracle preflights and emulates CASE indexes; this is NOT Oracle certification,
 * government verification, actual physical cash receipt, or a live account-opening smoke test.
 */
@SpringBootTest(classes = NexaApiApplication.class, properties = {
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.default_schema=PUBLIC", "spring.jpa.open-in-view=false",
    "spring.jpa.show-sql=false", "logging.level.root=WARN",
    "spring.datasource.hikari.schema=PUBLIC", "spring.datasource.hikari.connection-init-sql=SELECT 1",
    "spring.sql.init.mode=never", "spring.config.import=", "nexa.onboarding.enabled=true",
    "nexa.onboarding.identity.key-id=http-test-identity",
    "nexa.onboarding.identity.encryption-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "nexa.onboarding.documents.enabled=false", "nexa.onboarding.documents.root=",
    "nexa.onboarding.documents.key-id=", "nexa.onboarding.documents.encryption-key=",
    "nexa.admin.bootstrap.email=", "nexa.admin.bootstrap.password=",
    "nexa.security.jwt.secret=isolated-http-onboarding-secret-with-more-than-32-bytes",
    "nexa.security.jwt.issuer=isolated-http-onboarding", "nexa.ai.enabled=false",
    "nexa.cors.allowed-origins=http://localhost:8000"
})
@ActiveProfiles("integration")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthenticatedAccountOpeningIntegrationTest {
  private static final String CUSTOMER = "/api/v1/account-applications";
  private static final String ADMIN = "/api/v1/admin/account-applications";
  private static final String PASSWORD = "SyntheticHttp@Test2026!";
  private static final Connection KEEP_ALIVE = initialize();


  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
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
  private Session customer;
  private Session administrator;

  @BeforeEach
  void onlyAnIsolatedDatabaseAndRealTestAuthentication() throws Exception {
    try (var connection = Objects.requireNonNull(db.getDataSource()).getConnection()) {
      assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:account-applications-");
    }
    for (String table : List.of("application_events", "opening_cash_receipts", "application_documents",
        "account_applications", "ledger_entries", "journal_entries", "transactions", "customer_credentials"))
      db.update("DELETE FROM " + table);
    db.update("DELETE FROM accounts WHERE account_category='CUSTOMER'");
    db.update("DELETE FROM customers");
    db.update("UPDATE accounts SET balance=0,status='ACTIVE'");
    customer = register("Customer");
    Session adminProfile = register("Administrator fixture");
    // Only this H2 fixture grants an admin role. Login then issues a real ADMIN-signed JWT.
    db.update("UPDATE customers SET role='ADMIN' WHERE user_id=?", adminProfile.userId());
    administrator = login(adminProfile);
  }

  @AfterAll static void closeKeeper() throws Exception { KEEP_ALIVE.close(); }

  @Test
  void registrationCreatesAProfileOnlyAndServerConsentIsEnforcedThroughHttp() throws Exception {
    assertThat(read("/api/v1/accounts", customer).size()).isZero();
    assertNoMoneyOrAccount();
    var requirements = read(CUSTOMER + "/requirements", customer);
    assertThat(requirements.get("applicationsAvailable").asBoolean()).isTrue();
    assertThat(requirements.get("identityDetailsAvailable").asBoolean()).isTrue();
    assertThat(requirements.get("consentVersion").asText()).isEqualTo("in-person-identity-v1");
    var request = createBody(requirements);
    request.put("consentVersion", "manual-onboarding-v1");
    write(CUSTOMER, customer, request, 400);
    assertThat(count("account_applications")).isZero();
    request.put("consentVersion", requirements.get("consentVersion").asText());
    request.put("openingAmount", "999.99");
    write(CUSTOMER, customer, request, 400);
    request.put("openingAmount", "1000.00");
    request.put("dateOfBirth", requirements.get("businessDate").asText());
    write(CUSTOMER, customer, request, 400);
    assertThat(count("account_applications")).isZero();
    assertNoMoneyOrAccount();
  }

  @Test
  void authenticatedIdentityReviewCashAndOpenCommitOneAccountAndOneAllocation() throws Exception {
    JsonNode application = draft(customer);
    String id = application.get("id").asText();
    String ciphertext = db.queryForObject("SELECT identity_ciphertext FROM account_applications WHERE id=?",String.class,id);
    assertThat(ciphertext).isNotBlank().doesNotContain("ABCPD1234E");
    assertThat(application.toString()).doesNotContain(ciphertext,"ABCPD1234E","http-test-identity");
    assertThat(application.get("identityMasked").asText()).isEqualTo("••••234E");
    assertThat(count("application_documents")).isZero();
    mvc.perform(get(ADMIN+"/"+id+"/identity").header("Authorization",administrator.authorization())).andExpect(status().isConflict());
    application = write(CUSTOMER + "/" + id + "/submit", customer, action(application), 200);
    assertThat(application.get("status").asText()).isEqualTo("PENDING_REVIEW");
    assertNoMoneyOrAccount();
    var unchecked = decision(application,"APPROVED"); unchecked.put("inPersonChecked",false);
    write(ADMIN+"/"+id+"/review",administrator,unchecked,400);
    var revealed = json.readTree(mvc.perform(get(ADMIN+"/"+id+"/identity").header("Authorization",administrator.authorization()))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control",containsString("no-store")))
        .andExpect(header().string("Cache-Control",containsString("private"))).andReturn().getResponse().getContentAsString());
    assertThat(revealed.get("identityNumber").asText()).isEqualTo("ABCPD1234E");
    assertThat(revealed.get("verificationMethod").asText()).isEqualTo("IN_PERSON_ORIGINAL");
    application = write(ADMIN + "/" + id + "/review", administrator, decision(application, "APPROVED"), 200);
    assertThat(application.get("status").asText()).isEqualTo("APPROVED_AWAITING_CASH");
    write(ADMIN + "/" + id + "/open", administrator, action(application), 409);
    assertNoMoneyOrAccount();
    var receipt = action(application);
    receipt.put("cashReceivedConfirmed", false);
    write(ADMIN + "/" + id + "/cash-receipts", administrator, receipt, 400);
    receipt.put("cashReceivedConfirmed", true);
    var tamperedAmount = new LinkedHashMap<String,Object>(receipt);
    tamperedAmount.put("amount", "9000.00");
    write(ADMIN + "/" + id + "/cash-receipts", administrator, tamperedAmount, 400);
    var tamperedNumber = new LinkedHashMap<String,Object>(receipt);
    tamperedNumber.put("receiptNumber", "CUSTOM-RECEIPT");
    write(ADMIN + "/" + id + "/cash-receipts", administrator, tamperedNumber, 400);
    application = write(ADMIN + "/" + id + "/cash-receipts", administrator, receipt, 200);
    assertThat(application.get("status").asText()).isEqualTo("CASH_RECEIVED");
    String generatedReceipt = application.get("receipts").get(0).get("receiptNumber").asText();
    assertThat(generatedReceipt).matches("NEXA-RCP-[A-F0-9]{32}");
    assertThat(application.get("receipts").get(0).get("amount").asText()).isEqualTo("1000.00");
    var receiptReplay = write(ADMIN + "/" + id + "/cash-receipts", administrator, receipt, 200);
    assertThat(receiptReplay.get("receipts").get(0).get("receiptNumber").asText()).isEqualTo(generatedReceipt);
    assertThat(count("opening_cash_receipts")).isEqualTo(1);
    assertThat(customerAccounts()).isZero();
    assertThat(balance("SYSTEM-CASH")).isEqualByComparingTo("1000");
    assertThat(balance("NEXA-OPENING-HOLD")).isEqualByComparingTo("1000");
    var opening = action(application);
    application = write(ADMIN + "/" + id + "/open", administrator, opening, 200);
    var replay = write(ADMIN + "/" + id + "/open", administrator, opening, 200);
    assertThat(application.get("status").asText()).isEqualTo("OPENED");
    assertThat(replay.get("accountId").asLong()).isEqualTo(application.get("accountId").asLong());
    assertThat(customerAccounts()).isEqualTo(1);
    assertThat(count("transactions")).isEqualTo(2);
    assertThat(count("journal_entries")).isEqualTo(2);
    assertThat(count("ledger_entries")).isEqualTo(4);
    assertThat(count("opening_cash_receipts")).isEqualTo(1);
    assertThat(balance("SYSTEM-CASH")).isEqualByComparingTo("1000");
    assertThat(balance("NEXA-OPENING-HOLD")).isZero();
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?", BigDecimal.class,
        application.get("accountId").asLong())).isEqualByComparingTo("1000");
    assertThat(read("/api/v1/accounts", customer).size()).isEqualTo(1);
    assertThat(db.queryForObject("SELECT account_number FROM accounts WHERE id=?", String.class,
        application.get("accountId").asLong())).matches("9[0-9]{11}");
    for (var difference : db.queryForList("SELECT SUM(CASE WHEN entry_type='DEBIT' THEN amount ELSE -amount END) AS delta FROM ledger_entries GROUP BY journal_entry_id"))
      assertThat((BigDecimal) difference.get("DELTA")).isZero();
  }

  @Test
  void actualJwtFiltersAndFreshOwnershipProtectIdentityUpdatesAndAdminReveal() throws Exception {
    mvc.perform(get(CUSTOMER)).andExpect(status().isUnauthorized());
    mvc.perform(get(CUSTOMER).header("Authorization","Bearer invalid-fixture-token")).andExpect(status().isUnauthorized());
    JsonNode application=draft(customer); String id=application.get("id").asText();
    Session other=register("Other customer");
    mvc.perform(get(CUSTOMER+"/"+id).header("Authorization",other.authorization())).andExpect(status().isNotFound());
    var correction=action(application); correction.put("identityType","AADHAAR"); correction.put("identityNumber","0123");
    write(CUSTOMER+"/"+id+"/identity",other,correction,404);
    mvc.perform(get(ADMIN+"/"+id+"/identity").header("Authorization",customer.authorization())).andExpect(status().isForbidden());
    write(ADMIN+"/"+id+"/review",customer,decision(application,"APPROVED"),403);
    write(ADMIN+"/"+id+"/open",customer,action(application),403);
    write(CUSTOMER+"/"+id+"/cancel",administrator,action(application),403);
    assertThat(count("application_events")).isEqualTo(1); assertNoMoneyOrAccount();
  }

  @Test
  void rejectedRequestNeverBecomesAnAccountAndItsReasonRemainsInPrivateAudit() throws Exception {
    JsonNode application = draft(customer);
    String id = application.get("id").asText();
    application = write(CUSTOMER + "/" + id + "/submit", customer, action(application), 200);
    application = write(ADMIN + "/" + id + "/review", administrator, decision(application, "REJECTED"), 200);
    assertThat(application.get("status").asText()).isEqualTo("REJECTED");
    assertThat(application.get("reviewReason").asText()).isEqualTo("Synthetic evidence review reason");
    write(ADMIN + "/" + id + "/open", administrator, action(application), 409);
    assertThat(read(CUSTOMER + "/" + id, customer).get("status").asText()).isEqualTo("REJECTED");
    assertThat(count("opening_cash_receipts")).isZero();
    assertNoMoneyOrAccount();
  }

  @Test
  void staleAdminJwtCannotApproveAfterTheDatabaseRoleIsRevoked() throws Exception {
    JsonNode application = draft(customer);
    String id = application.get("id").asText();
    application = write(CUSTOMER + "/" + id + "/submit", customer, action(application), 200);
    long events = count("application_events");
    db.update("UPDATE customers SET role='CUSTOMER' WHERE user_id=?", administrator.userId());
    mvc.perform(get(ADMIN+"/"+id+"/identity").header("Authorization",administrator.authorization())).andExpect(status().isForbidden());
    write(ADMIN + "/" + id + "/review", administrator, decision(application, "REJECTED"), 403);
    assertThat(count("application_events")).isEqualTo(events);
    assertThat(read(CUSTOMER + "/" + id, customer).get("status").asText()).isEqualTo("PENDING_REVIEW");
    assertNoMoneyOrAccount();
  }

  @Test
  void documentUploadsAreRetiredAndIdentityCorrectionsAreExplicitAndMasked() throws Exception {
    JsonNode application=draft(customer); String id=application.get("id").asText();
    mvc.perform(multipart(CUSTOMER+"/"+id+"/documents").part(new MockPart("file","fixture.png",new byte[]{1,2,3}))
        .header("Authorization",customer.authorization())).andExpect(status().isConflict());
    assertThat(count("application_documents")).isZero(); assertThat(count("application_events")).isEqualTo(1);
    var correction=action(application); correction.put("identityType","PASSPORT"); correction.put("identityNumber","AB123456");
    application=write(CUSTOMER+"/"+id+"/identity",customer,correction,200);
    assertThat(application.get("identityMasked").asText()).isEqualTo("••••3456");
    assertThat(application.toString()).doesNotContain("AB123456");
    assertThat(write(CUSTOMER+"/"+id+"/identity",customer,correction,200).get("version").asLong()).isEqualTo(application.get("version").asLong());
    correction.put("identityNumber","AB123457"); write(CUSTOMER+"/"+id+"/identity",customer,correction,409);
    application=write(CUSTOMER+"/"+id+"/submit",customer,action(application),200);
    assertThat(read(ADMIN+"/"+id+"/identity",administrator).get("identityNumber").asText()).isEqualTo("AB123456");
    assertThat(count("application_events")).isEqualTo(3); assertNoMoneyOrAccount();
  }

  @Test
  void fullAadhaarAndIdentifierCoercionsAreRejectedBeforeAnyApplicationExists() throws Exception {
    var request=createBody(read(CUSTOMER+"/requirements",customer));
    request.put("identityType","AADHAAR"); request.put("identityNumber","123456789012");
    write(CUSTOMER,customer,request,400); assertThat(count("account_applications")).isZero();
    request.put("identityNumber",1234); write(CUSTOMER,customer,request,400);
    request.put("identityNumber","0123"); JsonNode application=write(CUSTOMER,customer,request,201);
    assertThat(application.get("identityMasked").asText()).isEqualTo("••••0123");
    assertThat(db.queryForObject("SELECT identity_ciphertext FROM account_applications",String.class)).isNull();
    assertThat(db.queryForObject("SELECT identity_key_id FROM account_applications",String.class)).isNull();
    assertThat(db.queryForObject("SELECT identity_last4 FROM account_applications",String.class)).isEqualTo("0123");
    assertThat(write(CUSTOMER,customer,request,201).get("id").asText()).isEqualTo(application.get("id").asText());
    request.put("identityNumber","0124"); write(CUSTOMER,customer,request,409);
    assertThat(count("account_applications")).isEqualTo(1); assertNoMoneyOrAccount();
  }

  @Test
  void legacyCustomerAndAdminCreationCannotBypassReviewOrCreateMoney() throws Exception {
    write("/api/v1/accounts", customer, Map.of("displayName", "Savings", "accountType", "SAVINGS",
        "currencyCode", "INR", "dateOfBirth", "1990-01-01", "address", "Mumbai"), 409);
    write("/api/accounts", administrator, Map.of("accountNumber", "912345678901", "accountName", "Bypass",
        "accountType", "SAVINGS", "accountCategory", "CUSTOMER", "currencyCode", "INR"), 409);
    assertNoMoneyOrAccount();
  }

  @Test
  void aDisabledCustomerCannotReuseAnExistingAccessToken() throws Exception {
    db.update("UPDATE customers SET status='DISABLED' WHERE user_id=?", customer.userId());
    mvc.perform(get("/api/v1/accounts").header("Authorization",customer.authorization()))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(CUSTOMER).header("Authorization",customer.authorization()))
        .andExpect(status().isUnauthorized());
    assertNoMoneyOrAccount();
  }

  @Test
  void failedReceiptRollsBackMoneyAndTheSameRequestCanRecoverOnce() throws Exception {
    JsonNode application = draft(customer);
    String id = application.get("id").asText();
    application = write(CUSTOMER + "/" + id + "/submit", customer, action(application), 200);
    application = write(ADMIN + "/" + id + "/review", administrator, decision(application,"APPROVED"), 200);
    var receipt = action(application);
    receipt.put("cashReceivedConfirmed", true);
    db.execute("ALTER TABLE opening_cash_receipts ADD CONSTRAINT h2_reject_receipt CHECK (status<>'RECEIVED')");
    try {
      write(ADMIN + "/" + id + "/cash-receipts", administrator, receipt, 409);
      assertNoMoneyOrAccount();
      assertThat(count("opening_cash_receipts")).isZero();
      assertThat(read(ADMIN + "/" + id, administrator).get("status").asText()).isEqualTo("APPROVED_AWAITING_CASH");
    } finally {
      db.execute("ALTER TABLE opening_cash_receipts DROP CONSTRAINT h2_reject_receipt");
    }
    application = write(ADMIN + "/" + id + "/cash-receipts", administrator, receipt, 200);
    var replay = write(ADMIN + "/" + id + "/cash-receipts", administrator, receipt, 200);
    assertThat(replay.get("receipts").get(0).get("receiptNumber").asText())
        .isEqualTo(application.get("receipts").get(0).get("receiptNumber").asText());
    assertThat(count("transactions")).isEqualTo(1);
    assertThat(count("opening_cash_receipts")).isEqualTo(1);
    assertThat(balance("SYSTEM-CASH")).isEqualByComparingTo("1000");
    assertThat(balance("NEXA-OPENING-HOLD")).isEqualByComparingTo("1000");
  }

  @Test
  void exactCustomerAmountCanBeRefundedOnlyOnceWithoutCreatingAnAccount() throws Exception {
    var create = createBody(read(CUSTOMER + "/requirements", customer));
    create.put("openingAmount", "2500.37");
    JsonNode application = write(CUSTOMER, customer, create, 201);
    String id = application.get("id").asText();
    application = write(CUSTOMER + "/" + id + "/submit", customer, action(application), 200);
    application = write(ADMIN + "/" + id + "/review", administrator, decision(application,"APPROVED"), 200);
    var receipt = action(application); receipt.put("cashReceivedConfirmed", true);
    application = write(ADMIN + "/" + id + "/cash-receipts", administrator, receipt, 200);
    assertThat(application.get("receipts").get(0).get("amount").asText()).isEqualTo("2500.37");
    assertThat(balance("SYSTEM-CASH")).isEqualByComparingTo("2500.37");
    var requestRefund = action(application); requestRefund.put("reason", "Customer requested return of unallocated cash.");
    application = write(ADMIN + "/" + id + "/refund-request", administrator, requestRefund, 200);
    var refund = action(application); refund.put("cashReturnedConfirmed", true);
    application = write(ADMIN + "/" + id + "/refund", administrator, refund, 200);
    assertThat(application.get("status").asText()).isEqualTo("REFUNDED");
    assertThat(write(ADMIN + "/" + id + "/refund", administrator, refund, 200).get("status").asText()).isEqualTo("REFUNDED");
    var repeatedRefund = action(application); repeatedRefund.put("cashReturnedConfirmed", true);
    write(ADMIN + "/" + id + "/refund", administrator, repeatedRefund, 409);
    assertThat(customerAccounts()).isZero();
    assertThat(count("transactions")).isEqualTo(2);
    assertThat(count("opening_cash_receipts")).isEqualTo(1);
    assertThat(balance("SYSTEM-CASH")).isZero();
    assertThat(balance("NEXA-OPENING-HOLD")).isZero();
  }

  private Session register(String name) throws Exception {
    String email = "http-opening-" + UUID.randomUUID() + "@example.test";
    JsonNode response = json.readTree(mvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "fullName", "Synthetic " + name, "email", email, "password", PASSWORD))))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    return new Session(response.get("user").get("id").asText(), email, "Bearer " + response.get("accessToken").asText());
  }
  private Session login(Session user) throws Exception {
    JsonNode response = json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", user.email(), "password", PASSWORD))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    assertThat(response.get("user").get("role").asText()).isEqualTo("ADMIN");
    return new Session(user.userId(), user.email(), "Bearer " + response.get("accessToken").asText());
  }
  private JsonNode read(String url, Session user) throws Exception {
    return json.readTree(mvc.perform(get(url).header("Authorization", user.authorization()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }
  private JsonNode write(String url, Session user, Map<String, Object> body, int expected) throws Exception {
    return json.readTree(mvc.perform(post(url).header("Authorization", user.authorization())
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
        .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
  }
  private JsonNode draft(Session user) throws Exception { return write(CUSTOMER, user, createBody(read(CUSTOMER + "/requirements", user)), 201); }
  private Map<String,Object> createBody(JsonNode requirements) {
    var body = new LinkedHashMap<String,Object>();
    body.put("requestKey", UUID.randomUUID().toString()); body.put("accountType", "SAVINGS");
    body.put("currencyCode", "INR"); body.put("dateOfBirth", "1990-01-01"); body.put("openingAmount", "1000.00");
    body.put("identityType","PAN"); body.put("identityNumber","ABCPD1234E");
    body.put("consentVersion", requirements.get("consentVersion").asText()); body.put("consentAccepted", true);
    return body;
  }
  private Map<String,Object> action(JsonNode application) {
    var body = new LinkedHashMap<String,Object>();
    body.put("requestKey", UUID.randomUUID().toString()); body.put("expectedVersion", application.get("version").asLong());
    return body;
  }
  private Map<String,Object> decision(JsonNode application, String choice) {
    var body = action(application); body.put("decision", choice); body.put("reason", "Synthetic evidence review reason"); body.put("inPersonChecked","APPROVED".equals(choice)); return body;
  }
  private long count(String table) { return db.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
  private long customerAccounts() { return db.queryForObject("SELECT COUNT(*) FROM accounts WHERE account_category='CUSTOMER'", Long.class); }
  private BigDecimal balance(String number) { return db.queryForObject("SELECT balance FROM accounts WHERE account_number=?", BigDecimal.class, number); }
  private void assertNoMoneyOrAccount() {
    assertThat(customerAccounts()).isZero(); assertThat(count("transactions")).isZero();
    assertThat(count("journal_entries")).isZero(); assertThat(count("ledger_entries")).isZero();
    assertThat(balance("SYSTEM-CASH")).isZero(); assertThat(balance("NEXA-OPENING-HOLD")).isZero();
  }
  private record Session(String userId, String email, String authorization) {}

  private static Connection initialize() {
    try { return AccountApplicationTestDatabase.initializedDatabase(); }
    catch (Exception error) { throw new ExceptionInInitializerError(error); }
  }
}