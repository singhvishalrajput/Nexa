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

  @Test
  void registrationMayOmitPhoneButAccountApplicationsRequireAValidOne() throws Exception {
    assertThat(db.queryForObject("SELECT phone_number FROM customers WHERE user_id=?", String.class,customer.userId())).isNull();
    var request=createBody(read(CUSTOMER+"/requirements",customer)); request.remove("phoneNumber");
    JsonNode missing=write(CUSTOMER,customer,request,400);
    assertThat(missing.get("detail").asText()).contains("phone number");
    for(String invalid:List.of("", "12", "abcdefghij", "12+34567890", "1234567890123456", "9000\n000001")) {
      request.put("phoneNumber",invalid);write(CUSTOMER,customer,request,400);
    }
    assertThat(count("account_applications")).isZero();
    request.put("phoneNumber","+91 (90000) 00001");
    var application=write(CUSTOMER,customer,request,201);
    assertThat(application.get("phoneNumber").asText()).isEqualTo("+919000000001");
    assertThat(db.queryForObject("SELECT phone_number FROM customers WHERE user_id=?",String.class,customer.userId())).isNull();
    assertNoMoneyOrAccount();
  }

  @Test
  void aValidProfilePhoneIsUsedOnceWhenApplicationInputOmitsIt() throws Exception {
    db.update("UPDATE customers SET phone_number=? WHERE user_id=?","90000 00001",customer.userId());
    var request=createBody(read(CUSTOMER+"/requirements",customer));request.remove("phoneNumber");
    var application=write(CUSTOMER,customer,request,201);
    assertThat(application.get("phoneNumber").asText()).isEqualTo("9000000001");
    db.update("UPDATE customers SET phone_number=NULL WHERE user_id=?",customer.userId());
    assertThat(write(CUSTOMER,customer,request,201).get("id").asText()).isEqualTo(application.get("id").asText());
    assertThat(count("account_applications")).isEqualTo(1);
  }

  @Test
  void editableDetailsPreserveMaskedIdentityAndAreAtomicVersionedAndIdempotent() throws Exception {
    var application=draft(customer);String id=application.get("id").asText();
    String ciphertext=db.queryForObject("SELECT identity_ciphertext FROM account_applications WHERE id=?",String.class,id);
    var details=details(application,"+91 (98765) 43210","1994-02-03","2500.37");
    application=write(CUSTOMER+"/"+id+"/details",customer,details,200);
    assertThat(application.get("phoneNumber").asText()).isEqualTo("+919876543210");
    assertThat(application.get("dateOfBirth").asText()).isEqualTo("1994-02-03");
    assertThat(application.get("openingAmount").asText()).isEqualTo("2500.37");
    assertThat(application.get("identityMasked").asText()).isEqualTo("••••234E");
    assertThat(db.queryForObject("SELECT identity_ciphertext FROM account_applications WHERE id=?",String.class,id)).isEqualTo(ciphertext);
    assertThat(application.get("events").get(1).get("eventType").asText()).isEqualTo("APPLICATION_UPDATED");
    assertThat(write(CUSTOMER+"/"+id+"/details",customer,details,200).get("version").asLong()).isEqualTo(1);
    details.put("openingAmount","3000.00");write(CUSTOMER+"/"+id+"/details",customer,details,409);
    details.put("requestKey",UUID.randomUUID().toString());write(CUSTOMER+"/"+id+"/details",customer,details,409);
    assertThat(count("application_events")).isEqualTo(2);
    var invalid=details(application,"9876543210","1994-02-03","3000.00");invalid.put("identityType","AADHAAR");
    write(CUSTOMER+"/"+id+"/details",customer,invalid,400);
    for (var invalidValue : List.of(Map.entry("dateOfBirth","2020-01-01"),Map.entry("openingAmount","999.99"),
        Map.entry("openingAmount","1000.001"),Map.entry("phoneNumber","not-a-phone"))) {
      var rejected=details(application,"9876543210","1994-02-03","3000.00");
      rejected.put(invalidValue.getKey(),invalidValue.getValue());
      write(CUSTOMER+"/"+id+"/details",customer,rejected,400);
    }
    assertThat(read(CUSTOMER+"/"+id,customer).get("openingAmount").asText()).isEqualTo("2500.37");
    Session other=register("Other details owner");
    write(CUSTOMER+"/"+id+"/details",other,details(application,"9876543210","1994-02-03","3000.00"),404);
    write(CUSTOMER+"/"+id+"/details",administrator,details(application,"9876543210","1994-02-03","3000.00"),403);
    assertNoMoneyOrAccount();
  }

  @Test
  void requestedCorrectionsCanReplaceAllInputsThenFundOnlyTheReviewedAmount() throws Exception {
    var application=draft(customer);String id=application.get("id").asText();
    application=write(CUSTOMER+"/"+id+"/submit",customer,action(application),200);
    write(CUSTOMER+"/"+id+"/details",customer,details(application,"9876543210","1994-02-03","3200.50"),409);
    application=write(ADMIN+"/"+id+"/review",administrator,decision(application,"CHANGES_REQUESTED"),200);
    var correction=details(application,"98765-43210","1994-02-03","3200.50");
    correction.put("identityType","AADHAAR");correction.put("identityNumber","0123");
    application=write(CUSTOMER+"/"+id+"/details",customer,correction,200);
    assertThat(application.get("status").asText()).isEqualTo("CHANGES_REQUESTED");
    assertThat(application.get("identityMasked").asText()).isEqualTo("••••0123");
    assertThat(db.queryForObject("SELECT identity_ciphertext FROM account_applications WHERE id=?",String.class,id)).isNull();
    application=write(CUSTOMER+"/"+id+"/submit",customer,action(application),200);
    application=write(ADMIN+"/"+id+"/review",administrator,decision(application,"APPROVED"),200);
    write(CUSTOMER+"/"+id+"/details",customer,details(application,"9876543210","1994-02-03","9000"),409);
    var receipt=action(application);receipt.put("cashReceivedConfirmed",true);
    application=write(ADMIN+"/"+id+"/cash-receipts",administrator,receipt,200);
    assertThat(application.get("receipts").get(0).get("amount").asText()).isEqualTo("3200.50");
    write(CUSTOMER+"/"+id+"/details",customer,details(application,"9876543210","1994-02-03","9000"),409);
    application=write(ADMIN+"/"+id+"/open",administrator,action(application),200);
    assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?",BigDecimal.class,application.get("accountId").asLong())).isEqualByComparingTo("3200.50");
    assertThat(db.queryForObject("SELECT phone_number FROM customers WHERE user_id=?",String.class,customer.userId())).isEqualTo("9876543210");
  }

  @Test
  void legacyMissingPhoneRequiresCorrectionAtSubmissionReviewAndOpening() throws Exception {
    var application=draft(customer);String id=application.get("id").asText();
    db.update("UPDATE account_applications SET phone_number=NULL WHERE id=?",id);
    write(CUSTOMER+"/"+id+"/submit",customer,action(application),400);
    application=write(CUSTOMER+"/"+id+"/details",customer,details(application,"9876543210","1994-02-03","1000"),200);
    application=write(CUSTOMER+"/"+id+"/submit",customer,action(application),200);
    db.update("UPDATE account_applications SET phone_number=NULL WHERE id=?",id);
    write(ADMIN+"/"+id+"/review",administrator,decision(application,"APPROVED"),400);
    application=write(ADMIN+"/"+id+"/review",administrator,decision(application,"CHANGES_REQUESTED"),200);
    application=write(CUSTOMER+"/"+id+"/details",customer,details(application,"9876543210","1994-02-03","1000"),200);
    application=write(CUSTOMER+"/"+id+"/submit",customer,action(application),200);
    application=write(ADMIN+"/"+id+"/review",administrator,decision(application,"APPROVED"),200);
    var receipt=action(application);receipt.put("cashReceivedConfirmed",true);
    application=write(ADMIN+"/"+id+"/cash-receipts",administrator,receipt,200);
    db.update("UPDATE account_applications SET phone_number=NULL WHERE id=?",id);
    write(ADMIN+"/"+id+"/open",administrator,action(application),400);
    assertThat(customerAccounts()).isZero();assertThat(count("transactions")).isEqualTo(1);
  }

  @Test
  void correctingDobUsesTheCurrentEligibilityDateRatherThanAnOldDraftDate() throws Exception {
    var application=draft(customer);String id=application.get("id").asText();
    db.update("UPDATE account_applications SET business_date=DATE '2020-01-01' WHERE id=?",id);
    String cutoff=read(CUSTOMER+"/requirements",customer).get("latestDateOfBirth").asText();
    application=write(CUSTOMER+"/"+id+"/details",customer,details(application,"9876543210",cutoff,"1000"),200);
    assertThat(application.get("dateOfBirth").asText()).isEqualTo(cutoff);
    assertNoMoneyOrAccount();
  }

  @Test
  void detailsAndReplacementIdentityRollbackTogetherIfTheirAuditCannotBeSaved() throws Exception {
    var application=draft(customer);String id=application.get("id").asText();
    String before=db.queryForObject("SELECT identity_ciphertext FROM account_applications WHERE id=?",String.class,id);
    var correction=details(application,"9876543210","1994-02-03","3500.25");
    correction.put("identityType","AADHAAR");correction.put("identityNumber","0123");
    db.execute("ALTER TABLE application_events ADD CONSTRAINT h2_reject_details CHECK (event_type<>'APPLICATION_UPDATED')");
    try {
      write(CUSTOMER+"/"+id+"/details",customer,correction,409);
      var unchanged=read(CUSTOMER+"/"+id,customer);
      assertThat(unchanged.get("version").asLong()).isZero();
      assertThat(unchanged.get("openingAmount").asText()).isEqualTo("1000.00");
      assertThat(unchanged.get("dateOfBirth").asText()).isEqualTo("1990-01-01");
      assertThat(unchanged.get("phoneNumber").asText()).isEqualTo("9000000001");
      assertThat(db.queryForObject("SELECT identity_ciphertext FROM account_applications WHERE id=?",String.class,id)).isEqualTo(before);
      assertThat(count("application_events")).isEqualTo(1);
    } finally {
      db.execute("ALTER TABLE application_events DROP CONSTRAINT h2_reject_details");
    }
    assertThat(write(CUSTOMER+"/"+id+"/details",customer,correction,200).get("openingAmount").asText()).isEqualTo("3500.25");
    assertNoMoneyOrAccount();
  }

  @Test
  void anotherCustomersFormattedPhoneIsRejectedBeforeApplicationCreation() throws Exception {
    Session other=register("Existing phone owner");
    db.update("UPDATE customers SET phone_number=? WHERE user_id=?","(98765) 43210",other.userId());
    var create=createBody(read(CUSTOMER+"/requirements",customer));create.put("phoneNumber","9876543210");
    var conflict=write(CUSTOMER,customer,create,409);
    assertThat(conflict.get("detail").asText()).contains("phone number").contains("already in use");
    assertThat(count("account_applications")).isZero();
    db.update("UPDATE customers SET phone_number=? WHERE user_id=?","90000 00001",customer.userId());
    create.remove("phoneNumber");
    assertThat(write(CUSTOMER,customer,create,201).get("phoneNumber").asText()).isEqualTo("9000000001");
    assertNoMoneyOrAccount();
  }

  @Test
  void conflictingPhoneCorrectionLeavesTheOriginalDraftUnchanged() throws Exception {
    var application=draft(customer);String id=application.get("id").asText();
    Session other=register("Correction phone owner");
    db.update("UPDATE customers SET phone_number=? WHERE user_id=?","+91 (98765) 43210",other.userId());
    var correction=details(application,"+919876543210","1994-02-03","5000");
    var conflict=write(CUSTOMER+"/"+id+"/details",customer,correction,409);
    assertThat(conflict.get("detail").asText()).contains("phone number");
    var unchanged=read(CUSTOMER+"/"+id,customer);
    assertThat(unchanged.get("phoneNumber").asText()).isEqualTo("9000000001");
    assertThat(unchanged.get("openingAmount").asText()).isEqualTo("1000.00");
    assertThat(unchanged.get("version").asLong()).isZero();
    assertThat(count("application_events")).isEqualTo(1);
    assertNoMoneyOrAccount();
  }

  @Test
  void newlyConflictingPhoneIsRecheckedAtSubmissionApprovalReceiptAndOpening() throws Exception {
    var application=draft(customer);String id=application.get("id").asText();
    Session other=register("Later phone owner");
    db.update("UPDATE customers SET phone_number=? WHERE user_id=?","90000 00001",other.userId());
    write(CUSTOMER+"/"+id+"/submit",customer,action(application),409);
    db.update("UPDATE customers SET phone_number=NULL WHERE user_id=?",other.userId());
    application=write(CUSTOMER+"/"+id+"/submit",customer,action(application),200);
    db.update("UPDATE customers SET phone_number=? WHERE user_id=?","(90000) 00001",other.userId());
    write(ADMIN+"/"+id+"/review",administrator,decision(application,"APPROVED"),409);
    db.update("UPDATE customers SET phone_number=NULL WHERE user_id=?",other.userId());
    application=write(ADMIN+"/"+id+"/review",administrator,decision(application,"APPROVED"),200);
    var receipt=action(application);receipt.put("cashReceivedConfirmed",true);
    db.update("UPDATE customers SET phone_number=? WHERE user_id=?","9000000001",other.userId());
    write(ADMIN+"/"+id+"/cash-receipts",administrator,receipt,409);
    assertNoMoneyOrAccount();assertThat(count("opening_cash_receipts")).isZero();
    db.update("UPDATE customers SET phone_number=NULL WHERE user_id=?",other.userId());
    application=write(ADMIN+"/"+id+"/cash-receipts",administrator,receipt,200);
    db.update("UPDATE customers SET phone_number=? WHERE user_id=?","9000000001",other.userId());
    var conflict=write(ADMIN+"/"+id+"/open",administrator,action(application),409);
    assertThat(conflict.get("detail").asText()).contains("phone number");
    assertThat(customerAccounts()).isZero();assertThat(count("transactions")).isEqualTo(1);
    var requestRefund=action(application);requestRefund.put("reason","Return opening cash after contact conflict.");
    application=write(ADMIN+"/"+id+"/refund-request",administrator,requestRefund,200);
    var refund=action(application);refund.put("cashReturnedConfirmed",true);
    application=write(ADMIN+"/"+id+"/refund",administrator,refund,200);
    assertThat(application.get("status").asText()).isEqualTo("REFUNDED");
    assertThat(balance("SYSTEM-CASH")).isZero();assertThat(balance("NEXA-OPENING-HOLD")).isZero();
  }

  @Test
  void lateProfilePhoneConstraintFailureRollsBackTheWholeOpeningAndCanBeRetried() throws Exception {
    var application=draft(customer);String id=application.get("id").asText();
    application=write(CUSTOMER+"/"+id+"/submit",customer,action(application),200);
    application=write(ADMIN+"/"+id+"/review",administrator,decision(application,"APPROVED"),200);
    var receipt=action(application);receipt.put("cashReceivedConfirmed",true);
    application=write(ADMIN+"/"+id+"/cash-receipts",administrator,receipt,200);
    var opening=action(application);
    // Fail only the final phone persistence, after the service availability check and allocation.
    db.execute("ALTER TABLE customers ADD CONSTRAINT h2_reject_phone_write CHECK (phone_number IS NULL)");
    try {
      var conflict=write(ADMIN+"/"+id+"/open",administrator,opening,409);
      assertThat(conflict.get("detail").asText()).contains("phone number").contains("refund");
      assertThat(customerAccounts()).isZero();assertThat(count("transactions")).isEqualTo(1);
      assertThat(count("journal_entries")).isEqualTo(1);assertThat(count("ledger_entries")).isEqualTo(2);
      assertThat(balance("SYSTEM-CASH")).isEqualByComparingTo("1000");
      assertThat(balance("NEXA-OPENING-HOLD")).isEqualByComparingTo("1000");
      assertThat(read(ADMIN+"/"+id,administrator).get("status").asText()).isEqualTo("CASH_RECEIVED");
      assertThat(db.queryForObject("SELECT status FROM opening_cash_receipts WHERE application_id=?",String.class,id)).isEqualTo("RECEIVED");
    } finally {
      db.execute("ALTER TABLE customers DROP CONSTRAINT h2_reject_phone_write");
    }
    assertThat(write(ADMIN+"/"+id+"/open",administrator,opening,200).get("status").asText()).isEqualTo("OPENED");
    assertThat(customerAccounts()).isEqualTo(1);assertThat(count("transactions")).isEqualTo(2);
  }

  private Map<String,Object> details(JsonNode application,String phone,String birth,String amount) {
    var result=action(application);result.put("phoneNumber",phone);result.put("dateOfBirth",birth);result.put("openingAmount",amount);return result;
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
    body.put("currencyCode", "INR"); body.put("phoneNumber", "9000000001"); body.put("dateOfBirth", "1990-01-01"); body.put("openingAmount", "1000.00");
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