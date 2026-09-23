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
class LoanIntegrationTest {
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
                : "jdbc:h2:mem:loanbank;MODE=Oracle;DB_CLOSE_DELAY=-1");
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
  @Autowired com.nexa.api.repository.UserRepository users;
  @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
  String auth, otherAuth, source, destination, email;

  JsonNode postJson(String path, Object data, String token, int status) throws Exception {
    var q = path.equals("/api/v1/loans") ? LoanTestSupport.application(json.writeValueAsString(data)) :
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
    if (!ORACLE
        && db.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE account_number='NEXA-LOAN-INTEREST'",
                Integer.class)
            == 0) {
      // Exercise the real additive migration, rather than only Hibernate's generated columns.
      for (String column :
          List.of("application_key", "loan_purpose", "tenure_months", "approved_at", "closed_at"))
        db.execute("ALTER TABLE accounts DROP COLUMN " + column);
      for (String column :
          List.of("installment_number", "principal_component", "interest_component"))
        db.execute("ALTER TABLE transactions DROP COLUMN " + column);
      db.execute(
          "ALTER TABLE transactions ADD CONSTRAINT ck_tx_record_kind CHECK(record_kind IS NOT"
              + " NULL)");
      var migration =
          new org.springframework.core.io.ClassPathResource(
              "db/migration/V20__loan_amortization.sql");
      String sql;
      try (var input = migration.getInputStream()) {
        sql = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      }
      // H2 has no Oracle function-based indexes; NULLS DISTINCT provides the same scope.
      sql =
          sql.replace(
                  "CREATE UNIQUE INDEX uk_loan_application ON accounts(CASE WHEN application_key IS"
                      + " NOT NULL THEN customer_id END, application_key)",
                  "ALTER TABLE accounts ADD CONSTRAINT uk_loan_application UNIQUE NULLS"
                      + " DISTINCT(customer_id, application_key)")
              .replace(
                  "CREATE UNIQUE INDEX uk_loan_installment ON transactions(CASE WHEN"
                      + " installment_number IS NOT NULL THEN target_id END, installment_number)",
                  "ALTER TABLE transactions ADD CONSTRAINT uk_loan_installment UNIQUE NULLS"
                      + " DISTINCT(target_id, installment_number)");
      new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
              new org.springframework.core.io.ByteArrayResource(
                  sql.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
          .execute(db.getDataSource());
    }
    if (!ORACLE
        && db.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE account_number='NEXA-BANK-FUNDING'",
                Integer.class)
            == 0) {
      for (String col : List.of("reviewed_by", "review_reason", "reviewed_at"))
        db.execute("ALTER TABLE accounts DROP COLUMN " + col);
      new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
              new org.springframework.core.io.ClassPathResource(
                  "db/migration/V21__admin_loan_approval_and_bank_funding.sql"))
          .execute(db.getDataSource());
      db.update("UPDATE accounts SET balance=10000000 WHERE account_number='NEXA-BANK-FUNDING'");
      db.execute("DROP TABLE loan_salary_slips");
      new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
          new org.springframework.core.io.ClassPathResource("db/migration/V23__loan_salary_slips.sql"))
          .execute(db.getDataSource());
    }
    LoanTestSupport.bank(db);
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

  Map<String, Object> application(String key, int months) {
    return Map.of(
        "accountId",
        Long.valueOf(source),
        "applicationKey",
        key,
        "purpose",
        "Education",
        "amount",
        "12000",
        "tenureMonths",
        months);
  }

  String apply(String key, int months) throws Exception {
    var loan = postJson("/api/v1/loans", application(key, months), auth, 201);
    assertThat(loan.get("status").asText()).isEqualTo("PENDING_APPROVAL");
    assertThat(new BigDecimal(loan.get("outstanding").asText())).isZero();
    assertThat(loan.at("/terms/annualInterestRate").decimalValue()).isEqualByComparingTo("14.50");
    approve(loan.get("id").asText());
    return loan.get("id").asText();
  }

  String adminToken() throws Exception {
    String email = "admin-" + UUID.randomUUID() + "@example.com";
    new com.nexa.api.service.AdminBootstrap(users, passwords, email, "Administrator@Test12345")
        .run(new org.springframework.boot.DefaultApplicationArguments());
    return "Bearer "
        + postJson(
                "/api/v1/auth/login",
                Map.of("email", email, "password", "Administrator@Test12345"),
                null,
                200)
            .get("accessToken")
            .asText();
  }

  void approve(String id) throws Exception {
    postJson(
        "/api/v1/admin/loans/" + id + "/approve",
        Map.of("reason", "Approved for integration scenario", "verifiedSalarySlipIds", documentIds(id)),
        adminToken(),
        200);
  }

  String root(String id) {
    return "/api/v1/loans/" + id;
  }

  List<String> documentIds(String id) {
    return db.queryForList("SELECT d.id FROM loan_salary_slips d JOIN accounts a ON a.id=d.loan_account_id"
        + " WHERE a.product_id=? ORDER BY d.salary_month", String.class, id);
  }

  @Test
  void salarySlipsAreMandatoryAndInvalidUploadsRollBackTheApplication() throws Exception {
    mvc.perform(post("/api/v1/loans").header("Authorization", auth)
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(application("no-documents", 3))))
        .andExpect(status().isBadRequest());
    var missing = multipart("/api/v1/loans").header("Authorization", auth)
        .file(new org.springframework.mock.web.MockMultipartFile("application", "", "application/json",
            json.writeValueAsBytes(application("missing-documents", 3))));
    mvc.perform(missing).andExpect(status().isBadRequest());
    var bad = LoanTestSupport.application(json.writeValueAsString(application("bad-documents", 3)));
    bad.file(new org.springframework.mock.web.MockMultipartFile("files", "extra.pdf", "application/pdf",
        "%PDF-extra".getBytes()));
    mvc.perform(bad.header("Authorization", auth)).andExpect(status().isBadRequest());
    assertThat(db.queryForObject("SELECT COUNT(*) FROM accounts WHERE application_key IN"
        + " ('no-documents','missing-documents','bad-documents')", Integer.class)).isZero();
  }

  @Test
  void salarySlipsRejectWrongMonthsDuplicateContentUnsupportedTypesAndOversizedFiles() throws Exception {
    var current = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Kolkata"));
    for (String problem : List.of("wrong-month", "duplicate", "unsupported", "oversized")) {
      var request = multipart("/api/v1/loans")
          .file(new org.springframework.mock.web.MockMultipartFile("application", "", "application/json",
              json.writeValueAsBytes(application(problem, 3))));
      for (int i = 3; i >= 1; i--) {
        String month = current.minusMonths(i).toString();
        request.param("months", problem.equals("wrong-month") && i == 1 ? current.toString() : month);
        if (problem.equals("unsupported") && i == 1)
          request.file(new org.springframework.mock.web.MockMultipartFile("files", "fake.pdf", "application/pdf",
              "<html>not a PDF</html>".getBytes()));
        else if (problem.equals("oversized") && i == 1)
          request.file(new org.springframework.mock.web.MockMultipartFile("files", "large.pdf", "application/pdf",
              new byte[com.nexa.api.service.LoanDocumentService.MAX_BYTES + 1]));
        else request.file(LoanTestSupport.slip(problem.equals("duplicate") ? "same-file" : month));
      }
      mvc.perform(request.header("Authorization", auth)).andExpect(status().isBadRequest());
      assertThat(db.queryForObject("SELECT COUNT(*) FROM accounts WHERE application_key=?", Integer.class, problem)).isZero();
    }
  }

  @Test
  void salarySlipsArePrivateAndAdminMustExplicitlyVerifyAllThreeBeforeApproval() throws Exception {
    String id = postJson("/api/v1/loans", application("salary-review", 3), auth, 201).get("id").asText();
    String path = root(id) + "/salary-slips", admin = adminToken();
    var bundle = getJson(path, auth);
    assertThat(bundle.get("documents").size()).isEqualTo(3);
    assertThat(bundle.toString()).doesNotContain("fileContent", "%PDF");
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(get(path).header("Authorization", otherAuth)).andExpect(status().isNotFound());
    String doc = documentIds(id).get(0);
    mvc.perform(get(path + "/" + doc).header("Authorization", otherAuth)).andExpect(status().isNotFound());
    mvc.perform(get(path + "/" + doc).header("Authorization", admin))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
        .andExpect(content().contentType("application/pdf"));
    String approval = "/api/v1/admin/loans/" + id + "/approve";
    postJson(approval, Map.of("reason", "Checked"), admin, 400);
    postJson(approval, Map.of("reason", "Checked", "verifiedSalarySlipIds", List.of(doc)), admin, 400);
    postJson(approval, Map.of("reason", "Checked", "verifiedSalarySlipIds", documentIds(id)), auth, 403);
    assertThat(getJson(root(id), auth).get("status").asText()).isEqualTo("PENDING_APPROVAL");
    postJson(approval, Map.of("reason", "All three slips cross-checked", "verifiedSalarySlipIds", documentIds(id)), admin, 200);
    assertThat(getJson(path, auth).get("documents")).allSatisfy(slip -> {
      assertThat(slip.get("verifiedBy").asText()).isNotBlank();
      assertThat(slip.get("verifiedAt").isNull()).isFalse();
    });
    var replay = postJson("/api/v1/loans", application("salary-review", 3), auth, 201);
    assertThat(replay.get("id").asText()).isEqualTo(id);
    assertThat(documentIds(id)).hasSize(3);
  }

  @Test
  void olderPendingApplicationCanSupplySlipsButCannotBeApprovedWithoutThem() throws Exception {
    String id = postJson("/api/v1/loans", application("old-pending", 3), auth, 201).get("id").asText();
    db.update("DELETE FROM loan_salary_slips WHERE loan_account_id=(SELECT id FROM accounts WHERE product_id=?)", id);
    postJson("/api/v1/admin/loans/" + id + "/approve",
        Map.of("reason", "Missing slips", "verifiedSalarySlipIds", List.of("a","b","c")), adminToken(), 400);
    String path = root(id) + "/salary-slips";
    var request = multipart(path);
    for (var month : getJson(path, auth).get("requiredMonths"))
      request.param("months", month.asText()).file(LoanTestSupport.slip(month.asText()));
    mvc.perform(request.header("Authorization", otherAuth)).andExpect(status().isNotFound());
    var ownerRequest = multipart(path);
    for (var month : getJson(path, auth).get("requiredMonths"))
      ownerRequest.param("months", month.asText()).file(LoanTestSupport.slip(month.asText()));
    mvc.perform(ownerRequest.header("Authorization", auth)).andExpect(status().isOk());
    approve(id);
    assertThat(getJson(root(id), auth).get("status").asText()).isEqualTo("APPROVED");
  }

  Map<String, Object> prepayment(String id, BigDecimal amount, String option) throws Exception {
    var preview = postJson(root(id) + "/repayment-preview", Map.of("amount", amount), auth, 200);
    return Map.of("amount", amount, "requestId", UUID.randomUUID().toString(),
        "prepaymentOption", option, "previewToken", preview.get("previewToken").asText());
  }

  String pay(String id, String installment) {
    return root(id) + "/installments/" + installment + "/pay";
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
  void completeLifecyclePostsPrincipalAndInterestAndPreservesReadModels() throws Exception {
    var quote =
        postJson("/api/v1/loans/quote", Map.of("amount", "12000", "tenureMonths", 3), auth, 200);
    String id = apply("lifecycle", 3);
    assertThat(balance(source)).isEqualByComparingTo("1000");
    assertThat(getJson(root(id) + "/schedule", auth).size()).isZero();
    assertThat(postJson(root(id) + "/accept", Map.of(), auth, 200).get("status").asText())
        .isEqualTo("ACTIVE");
    postJson(root(id) + "/accept", Map.of(), auth, 200);
    assertThat(balance(source)).isEqualByComparingTo("13000");
    var schedule = getJson(root(id) + "/schedule", auth);
    assertThat(schedule.size()).isEqualTo(3);
    BigDecimal paid = BigDecimal.ZERO;
    for (var row : schedule) {
      String path = pay(id, row.get("id").asText());
      var receipt = postJson(path, Map.of(), auth, 200);
      assertThat(receipt.get("principalAmount")).isEqualTo(row.get("principalAmount"));
      assertThat(receipt.get("interestAmount")).isEqualTo(row.get("interestAmount"));
      assertThat(postJson(path, Map.of(), auth, 200)).isEqualTo(receipt);
      paid = paid.add(receipt.get("amount").decimalValue());
    }
    assertThat(paid).isEqualByComparingTo(quote.get("totalRepayment").decimalValue());
    assertThat(balance(source)).isEqualByComparingTo(new BigDecimal("13000").subtract(paid));
    var detail = getJson(root(id), auth);
    assertThat(detail.get("status").asText()).isEqualTo("CLOSED");
    assertThat(new BigDecimal(detail.get("outstanding").asText())).isZero();
    assertThat(detail.at("/terms/closedAt").isNull()).isFalse();
    assertThat(detail.get("paymentHistory").size()).isEqualTo(3);
    assertThat(getJson(root(id) + "/payments", auth).size()).isEqualTo(4);
    postJson(root(id) + "/accept", Map.of(), auth, 200);
    assertThat(balance(source)).isEqualByComparingTo(new BigDecimal("13000").subtract(paid));
    balanced();
  }

  @Test
  void validationsOwnershipAndOrderingUseExistingApiErrors() throws Exception {
    mvc.perform(post("/api/v1/loans/quote").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    var bad =
        postJson("/api/v1/loans/quote", Map.of("amount", "999.99", "tenureMonths", 3), auth, 400);
    assertThat(bad.get("code").asText()).isEqualTo("INVALID_REQUEST");
    postJson("/api/v1/loans", application("foreign", 2), otherAuth, 404);
    String id = apply("validation", 2);
    for (String suffix : List.of("", "/schedule", "/payments", "/account"))
      mvc.perform(get(root(id) + suffix).header("Authorization", otherAuth))
          .andExpect(status().isNotFound());
    postJson(root(id) + "/accept", Map.of(), otherAuth, 404);
    postJson(root(id) + "/accept", Map.of(), auth, 200);
    var rows = getJson(root(id) + "/schedule", auth);
    postJson(pay(id, rows.get(0).get("id").asText()), Map.of(), otherAuth, 404);
    postJson(pay(id, rows.get(1).get("id").asText()), Map.of(), auth, 400);
    db.update("UPDATE accounts SET balance=1 WHERE id=?", Long.valueOf(source));
    postJson(pay(id, rows.get(0).get("id").asText()), Map.of(), auth, 400);
    assertThat(getJson(root(id) + "/payments", auth).size()).isEqualTo(1);
    assertThat(new BigDecimal(getJson(root(id), auth).get("outstanding").asText()))
        .isEqualByComparingTo("12000");
    db.update("UPDATE accounts SET status='BLOCKED' WHERE id=?", Long.valueOf(source));
    postJson("/api/v1/loans", application("blocked", 2), auth, 400);
    postJson(pay(id, rows.get(0).get("id").asText()), Map.of(), auth, 400);
  }

  @Test
  void applicationRetriesAndConcurrentAcceptAndPaymentPostOnce() throws Exception {
    var requests =
        together(() -> postJson("/api/v1/loans", application("concurrent", 3), auth, 201));
    String id = requests.get(0).get("id").asText();
    assertThat(requests.get(1).get("id").asText()).isEqualTo(id);
    postJson("/api/v1/loans", application("concurrent", 4), auth, 409);
    approve(id);
    together(() -> postJson(root(id) + "/accept", Map.of(), auth, 200));
    assertThat(balance(source)).isEqualByComparingTo("13000");
    var row = getJson(root(id) + "/schedule", auth).get(0);
    var receipts = together(() -> postJson(pay(id, row.get("id").asText()), Map.of(), auth, 200));
    assertThat(receipts.get(0)).isEqualTo(receipts.get(1));
    assertThat(balance(source))
        .isEqualByComparingTo(
            new BigDecimal("13000").subtract(row.get("totalAmount").decimalValue()));
    assertThat(getJson(root(id) + "/payments", auth).size()).isEqualTo(2);
    balanced();
  }

  @Test
  void repaymentMinimumAndFinalPaymentSupportRequestRetries() throws Exception {
    String id = apply("compatibility", 1);
    postJson(root(id) + "/disburse", Map.of(), auth, 200);
    postJson(
        root(id) + "/repay",
        Map.of("amount", "10", "requestId", UUID.randomUUID().toString()),
        auth,
        400);
    var row = getJson(root(id) + "/schedule", auth).get(0);
    var request =
        Map.of(
            "amount",
            row.get("totalAmount").decimalValue(),
            "requestId",
            UUID.randomUUID().toString());
    var receipt = postJson(root(id) + "/repay", request, auth, 200);
    assertThat(postJson(root(id) + "/repay", request, auth, 200)).isEqualTo(receipt);
    assertThat(getJson(root(id), auth).get("status").asText()).isEqualTo("CLOSED");
    balanced();
  }

  @Test
  void extraRepaymentReducesPrincipalAndInterestAndPreservesPaidHistory() throws Exception {
    String id = apply("extra-principal", 6);
    postJson(root(id) + "/accept", Map.of(), auth, 200);
    var before = getJson(root(id) + "/schedule", auth);
    var first = before.get(0);
    BigDecimal emi = first.get("totalAmount").decimalValue();
    BigDecimal payment = emi.add(new BigDecimal("5000"));
    var request = prepayment(id, payment, "REDUCE_TENURE");
    var responses = together(() -> postJson(root(id) + "/repay", request, auth, 200));
    assertThat(responses.get(0)).isEqualTo(responses.get(1));
    BigDecimal principal = first.get("principalAmount").decimalValue().add(new BigDecimal("5000"));
    BigDecimal remaining = new BigDecimal("12000").subtract(principal);
    var detail = getJson(root(id), auth);
    assertThat(new BigDecimal(detail.get("outstanding").asText())).isEqualByComparingTo(remaining);
    assertThat(detail.at("/terms/emiAmount").decimalValue()).isEqualByComparingTo(emi);
    assertThat(balance(source)).isEqualByComparingTo(new BigDecimal("13000").subtract(payment));
    var after = getJson(root(id) + "/schedule", auth);
    assertThat(after.size()).isLessThan(before.size());
    assertThat(after.get(0).get("status").asText()).isEqualTo("PAID");
    assertThat(after.get(0).get("principalAmount")).isEqualTo(first.get("principalAmount"));
    BigDecimal projectedPrincipal = BigDecimal.ZERO;
    for (int i = 1; i < after.size(); i++) {
      projectedPrincipal = projectedPrincipal.add(after.get(i).get("principalAmount").decimalValue());
      assertThat(after.get(i).get("dueDate")).isEqualTo(before.get(i).get("dueDate"));
      assertThat(after.get(i).get("interestAmount").decimalValue())
          .isLessThan(before.get(i).get("interestAmount").decimalValue());
      if (i < after.size() - 1)
        assertThat(after.get(i).get("totalAmount").decimalValue()).isEqualByComparingTo(emi);
    }
    assertThat(projectedPrincipal).isEqualByComparingTo(remaining);
    String tx = responses.get(0).get("transactionId").asText();
    assertThat(db.queryForObject("SELECT principal_component FROM transactions WHERE id=?",
        BigDecimal.class, tx)).isEqualByComparingTo(principal);
    assertThat(db.queryForObject("SELECT interest_component FROM transactions WHERE id=?",
        BigDecimal.class, tx)).isEqualByComparingTo(first.get("interestAmount").decimalValue());
    postJson(root(id) + "/repay", Map.of("amount", payment.add(BigDecimal.ONE),
        "requestId", request.get("requestId")), auth, 409);
    balanced();
  }

  @Test
  void comparisonIsReadOnlyAndReducedEmiMatchesPreviewWithoutChangingRateOrDates() throws Exception {
    String id = apply("compare-emi", 12);
    postJson(root(id) + "/accept", Map.of(), auth, 200);
    var before = getJson(root(id) + "/schedule", auth);
    BigDecimal bankBefore = balance(source);
    var preview = postJson(root(id) + "/repayment-preview", Map.of("amount", "5000"), auth, 200);
    assertThat(getJson(root(id) + "/schedule", auth)).isEqualTo(before);
    assertThat(balance(source)).isEqualByComparingTo(bankBefore);
    assertThat(preview.get("annualInterestRate").decimalValue()).isEqualByComparingTo("14.50");
    var reduced = preview.get("options").get(1);
    assertThat(reduced.get("option").asText()).isEqualTo("REDUCE_EMI");
    assertThat(reduced.get("remainingInstallments").asInt()).isEqualTo(11);
    assertThat(reduced.get("finalDueDate")).isEqualTo(before.get(11).get("dueDate"));
    assertThat(preview.at("/options/0/futureInterest").decimalValue())
        .isLessThan(reduced.get("futureInterest").decimalValue());
    var request = Map.of("amount", "5000", "requestId", UUID.randomUUID().toString(),
        "prepaymentOption", "REDUCE_EMI", "previewToken", preview.get("previewToken").asText());
    var response = postJson(root(id) + "/repay", request, auth, 200);
    assertThat(postJson(root(id) + "/repay", request, auth, 200)).isEqualTo(response);
    var after = getJson(root(id) + "/schedule", auth);
    assertThat(after.size()).isEqualTo(before.size());
    for (int i = 1; i < after.size(); i++) {
      assertThat(after.get(i).get("dueDate")).isEqualTo(before.get(i).get("dueDate"));
      assertThat(after.get(i).get("totalAmount")).isEqualTo(reduced.get("schedule").get(i - 1).get("totalAmount"));
    }
    var detail = getJson(root(id), auth);
    assertThat(detail.at("/terms/annualInterestRate").decimalValue()).isEqualByComparingTo("14.50");
    assertThat(detail.at("/terms/tenureMonths").asInt()).isEqualTo(12);
    assertThat(detail.at("/terms/emiAmount")).isEqualTo(reduced.get("regularEmi"));
    assertThat(db.queryForObject("SELECT audit_reason FROM transactions WHERE id=?", String.class,
        response.get("transactionId").asText())).startsWith("PREPAYMENT:REDUCE_EMI:");
    var changed = new HashMap<String, Object>(request);
    changed.put("prepaymentOption", "REDUCE_TENURE");
    postJson(root(id) + "/repay", changed, auth, 409);
    // A later top-up can choose the other strategy; it keeps the newly reduced regular EMI.
    var nextRequest = prepayment(id, new BigDecimal("1000"), "REDUCE_TENURE");
    postJson(root(id) + "/repay", nextRequest, auth, 200);
    assertThat(getJson(root(id), auth).at("/terms/emiAmount")).isEqualTo(reduced.get("regularEmi"));
    balanced();
  }

  @Test
  void missingChoiceStalePreviewAndCrossOwnerPreviewCannotPost() throws Exception {
    String id = apply("preview-safety", 6);
    postJson(root(id) + "/accept", Map.of(), auth, 200);
    postJson(root(id) + "/repayment-preview", Map.of("amount", "5000"), otherAuth, 404);
    postJson(root(id) + "/repay", Map.of("amount", "5000", "requestId", UUID.randomUUID().toString()), auth, 400);
    var old = prepayment(id, new BigDecimal("5000"), "REDUCE_TENURE");
    var first = getJson(root(id) + "/schedule", auth).get(0);
    postJson(root(id) + "/repay", Map.of("amount", first.get("totalAmount").decimalValue(),
        "requestId", UUID.randomUUID().toString()), auth, 200);
    BigDecimal before = balance(source);
    var schedule = getJson(root(id) + "/schedule", auth);
    postJson(root(id) + "/repay", old, auth, 409);
    assertThat(balance(source)).isEqualByComparingTo(before);
    assertThat(getJson(root(id) + "/schedule", auth)).isEqualTo(schedule);
    var wrong = new HashMap<String, Object>(prepayment(id, new BigDecimal("5000"), "REDUCE_EMI"));
    wrong.put("prepaymentOption", "INVALID");
    postJson(root(id) + "/repay", wrong, auth, 400);
    balanced();
  }

  @Test
  void extraPrincipalMinimumAppliesToCombinedPaymentsAndPayoffRemainsAllowed() throws Exception {
    String id = apply("extra-minimum", 6);
    postJson(root(id) + "/accept", Map.of(), auth, 200);
    var first = getJson(root(id) + "/schedule", auth).get(0);
    BigDecimal amount = first.get("totalAmount").decimalValue().add(new BigDecimal("10"));
    postJson(root(id) + "/repayment-preview", Map.of("amount", amount), auth, 400);
    postJson(root(id) + "/repay", Map.of("amount", amount, "requestId", UUID.randomUUID().toString()), auth, 400);
    var options = getJson(root(id) + "/repayment-options", auth);
    var payoff = postJson(root(id) + "/repayment-preview", Map.of("amount", options.get("maximumAmount").decimalValue()), auth, 200);
    assertThat(payoff.get("closesLoan").asBoolean()).isTrue();
    assertThat(payoff.get("options").get(0).get("remainingInstallments").asInt()).isZero();
    postJson(root(id) + "/repay", Map.of("amount", options.get("maximumAmount").decimalValue(),
        "requestId", UUID.randomUUID().toString()), auth, 200);
    assertThat(getJson(root(id), auth).get("status").asText()).isEqualTo("CLOSED");
    balanced();
  }

  @Test
  void additionalPaymentAfterEarlyEmiIsPrincipalOnlyAndCanCloseLoan() throws Exception {
    String id = apply("principal-topup", 6);
    postJson(root(id) + "/accept", Map.of(), auth, 200);
    var initial = getJson(root(id) + "/schedule", auth);
    postJson(pay(id, initial.get(0).get("id").asText()), Map.of(), auth, 200);
    var before = getJson(root(id) + "/schedule", auth);
    var options = getJson(root(id) + "/repayment-options", auth);
    assertThat(options.get("principalOnly").asBoolean()).isTrue();
    assertThat(options.get("minimumAmount").decimalValue())
        .isEqualByComparingTo(options.get("regularEmi").decimalValue());
    assertThat(options.get("interestAmount").decimalValue()).isZero();
    BigDecimal principalBefore = options.get("maximumAmount").decimalValue();
    postJson(root(id) + "/repay", Map.of("amount", "50.00", "requestId", UUID.randomUUID().toString()), auth, 400);
    var request = prepayment(id, new BigDecimal("2500"), "REDUCE_TENURE");
    var receipt = postJson(root(id) + "/repay", request, auth, 200);
    assertThat(postJson(root(id) + "/repay", request, auth, 200)).isEqualTo(receipt);
    var after = getJson(root(id) + "/schedule", auth);
    assertThat(after.get(0)).isEqualTo(before.get(0));
    assertThat(after.get(1).get("status").asText()).isEqualTo("PENDING");
    assertThat(after.get(1).get("interestAmount").decimalValue())
        .isLessThan(before.get(1).get("interestAmount").decimalValue());
    assertThat(db.queryForObject("SELECT interest_component FROM transactions WHERE id=?",
        BigDecimal.class, receipt.get("transactionId").asText())).isZero();
    assertThat(getJson(root(id) + "/payments", auth).toString()).contains("PRINCIPAL_PREPAYMENT");
    var payoff = getJson(root(id) + "/repayment-options", auth);
    assertThat(payoff.get("maximumAmount").decimalValue())
        .isEqualByComparingTo(principalBefore.subtract(new BigDecimal("2500")));
    var close = Map.of("amount", payoff.get("maximumAmount").decimalValue(),
        "requestId", UUID.randomUUID().toString());
    var closed = postJson(root(id) + "/repay", close, auth, 200);
    assertThat(postJson(root(id) + "/repay", close, auth, 200)).isEqualTo(closed);
    assertThat(getJson(root(id), auth).get("status").asText()).isEqualTo("CLOSED");
    assertThat(getJson(root(id) + "/schedule", auth).size()).isEqualTo(1);
    assertThat(new BigDecimal(getJson(root(id), auth).get("outstanding").asText())).isZero();
    balanced();
  }

  @Test
  void reducedFinalInstallmentIsAcceptedBelowRegularEmi() throws Exception {
    String id = apply("small-final", 6);
    postJson(root(id) + "/accept", Map.of(), auth, 200);
    postJson(root(id) + "/repay", prepayment(id, new BigDecimal("12000"), "REDUCE_TENURE"), auth, 200);
    var rows = getJson(root(id) + "/schedule", auth);
    assertThat(rows.size()).isEqualTo(2);
    assertThat(rows.get(1).get("principalAmount").decimalValue()).isEqualByComparingTo("145");
    assertThat(rows.get(1).get("totalAmount").decimalValue()).isEqualByComparingTo("146.75");
    var paid = postJson(pay(id, rows.get(1).get("id").asText()), Map.of(), auth, 200);
    assertThat(paid.get("amount").decimalValue()).isEqualByComparingTo("146.75");
    assertThat(getJson(root(id), auth).get("status").asText()).isEqualTo("CLOSED");
    balanced();
  }

  @Test
  void currentMonthAndOverdueInstallmentsPreventSmallPrincipalOnlyPayments() throws Exception {
    String id = apply("current-month", 6);
    postJson(root(id) + "/accept", Map.of(), auth, 200);
    var rows = getJson(root(id) + "/schedule", auth);
    postJson(pay(id, rows.get(0).get("id").asText()), Map.of(), auth, 200);
    // Simulate a later billing month: an unpaid installment is now overdue.
    db.update("UPDATE transactions SET due_at='2020-01-01' WHERE id=?", rows.get(1).get("id").asText());
    var options = getJson(root(id) + "/repayment-options", auth);
    assertThat(options.get("principalOnly").asBoolean()).isFalse();
    assertThat(options.get("minimumAmount").decimalValue())
        .isEqualByComparingTo(rows.get(1).get("totalAmount").decimalValue());
    postJson(root(id) + "/repay", Map.of("amount", "50", "requestId", UUID.randomUUID().toString()), auth, 400);
  }

  @Test
  void largerRepaymentsValidatePayoffFundsOwnershipAndAtomicRollback() throws Exception {
    String id = apply("prepayment-validation", 6);
    postJson(root(id) + "/accept", Map.of(), auth, 200);
    var before = getJson(root(id) + "/schedule", auth);
    mvc.perform(get(root(id) + "/repayment-options").header("Authorization", otherAuth))
        .andExpect(status().isNotFound());
    for (String amount : List.of("0", "-1", "0.001", "50", "12145.01"))
      postJson(root(id) + "/repay", Map.of("amount", amount, "requestId", UUID.randomUUID().toString()), auth, 400);
    db.update("UPDATE accounts SET balance=3000 WHERE id=?", Long.valueOf(source));
    var payment = prepayment(id, new BigDecimal("5000"), "REDUCE_EMI");
    postJson(root(id) + "/repay", payment, auth, 400);
    db.update("UPDATE accounts SET balance=13000 WHERE id=?", Long.valueOf(source));
    long bank = db.queryForObject("SELECT id FROM accounts WHERE account_number='NEXA-BANK-FUNDING'", Long.class);
    BigDecimal bankBefore = balance(Long.toString(bank));
    long lastJournal = db.queryForObject("SELECT MAX(id) FROM journal_entries", Long.class);
    db.execute("ALTER TABLE ledger_entries ADD CONSTRAINT prepayment_failure CHECK(account_id<>"
        + bank + " OR journal_entry_id<=" + lastJournal + ")");
    try {
      postJson(root(id) + "/repay", payment, auth, 503);
    } finally {
      db.execute("ALTER TABLE ledger_entries DROP CONSTRAINT prepayment_failure");
    }
    assertThat(balance(source)).isEqualByComparingTo("13000");
    assertThat(balance(Long.toString(bank))).isEqualByComparingTo(bankBefore);
    assertThat(getJson(root(id) + "/schedule", auth)).isEqualTo(before);
    assertThat(getJson(root(id) + "/payments", auth).size()).isEqualTo(1);
    assertThat(new BigDecimal(getJson(root(id), auth).get("outstanding").asText())).isEqualByComparingTo("12000");
    balanced();
  }

  @Test
  void overdueStateIsDerivedAndFilteredWithoutChangingSchedule() throws Exception {
    String id = apply("overdue", 3);
    postJson(root(id) + "/accept", Map.of(), auth, 200);
    db.update("UPDATE accounts SET due_at='2020-01-01' WHERE product_id=?", id);
    db.update(
        "UPDATE transactions SET due_at='2020-01-01' WHERE target_id=? AND installment_number=1",
        id);
    assertThat(getJson(root(id), auth).get("status").asText()).isEqualTo("OVERDUE");
    assertThat(getJson("/api/v1/loans?status=OVERDUE", auth).get(0).get("id").asText())
        .isEqualTo(id);
    assertThat(getJson("/api/v1/loans?status=ACTIVE", auth).size()).isZero();
    var row = getJson(root(id) + "/schedule", auth).get(0);
    assertThat(row.get("status").asText()).isEqualTo("OVERDUE");
    postJson(pay(id, row.get("id").asText()), Map.of(), auth, 200);
    assertThat(getJson(root(id), auth).get("status").asText()).isEqualTo("ACTIVE");
  }

  @Test
  void postingFailuresRollBackDisbursementAndEntireEmi() throws Exception {
    String id = apply("rollback", 3);
    db.execute(
        "ALTER TABLE ledger_entries ADD CONSTRAINT loan_test_failure CHECK(amount<>12000 OR"
            + " account_id<>"
            + source
            + ")");
    try {
      postJson(root(id) + "/accept", Map.of(), auth, 503);
    } finally {
      db.execute("ALTER TABLE ledger_entries DROP CONSTRAINT loan_test_failure");
    }
    assertThat(balance(source)).isEqualByComparingTo("1000");
    assertThat(getJson(root(id) + "/schedule", auth).size()).isZero();
    assertThat(getJson(root(id), auth).get("status").asText()).isEqualTo("APPROVED");
    postJson(root(id) + "/accept", Map.of(), auth, 200);
    var row = getJson(root(id) + "/schedule", auth).get(0);
    long income =
        db.queryForObject(
            "SELECT id FROM accounts WHERE account_number='NEXA-LOAN-INTEREST'", Long.class);
    BigDecimal incomeBefore = balance(Long.toString(income));
    // Fail the final interest credit after the principal and debit postings have been inserted.
    long lastJournal = db.queryForObject("SELECT MAX(id) FROM journal_entries", Long.class);
    db.execute(
        "ALTER TABLE ledger_entries ADD CONSTRAINT loan_test_failure CHECK(account_id<>"
            + income
            + " OR journal_entry_id<="
            + lastJournal
            + ")");
    try {
      postJson(pay(id, row.get("id").asText()), Map.of(), auth, 503);
    } finally {
      db.execute("ALTER TABLE ledger_entries DROP CONSTRAINT loan_test_failure");
    }
    assertThat(balance(source)).isEqualByComparingTo("13000");
    assertThat(balance(Long.toString(income))).isEqualByComparingTo(incomeBefore);
    assertThat(new BigDecimal(getJson(root(id), auth).get("outstanding").asText()))
        .isEqualByComparingTo("12000");
    assertThat(getJson(root(id) + "/payments", auth).size()).isEqualTo(1);
    assertThat(getJson(root(id) + "/schedule", auth).get(0).get("status").asText())
        .isEqualTo("PENDING");
    balanced();
  }

  private List<JsonNode> together(java.util.concurrent.Callable<JsonNode> task) throws Exception {
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.Callable<JsonNode> gated =
        () -> {
          start.await();
          return task.call();
        };
    try {
      var a = pool.submit(gated);
      var b = pool.submit(gated);
      start.countDown();
      return List.of(
          a.get(20, java.util.concurrent.TimeUnit.SECONDS),
          b.get(20, java.util.concurrent.TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void loansRequireAdminReviewAndBankFundsAndSettlementIsAtomic() throws Exception {
    String admin = adminToken();
    var applied = postJson("/api/v1/loans", application("review-required", 3), auth, 201);
    String id = applied.get("id").asText(), endpoint = "/api/v1/admin/loans/" + id;
    assertThat(applied.get("status").asText()).isEqualTo("PENDING_APPROVAL");
    assertThat(applied.at("/terms/approvedAt").isNull()).isTrue();
    postJson(root(id) + "/accept", Map.of(), auth, 400);
    postJson(root(id) + "/disburse", Map.of(), auth, 400);
    postJson(endpoint + "/approve", Map.of("reason", "Self approval"), auth, 403);
    mvc.perform(get("/api/v1/admin/loans").header("Authorization", otherAuth))
        .andExpect(status().isForbidden());
    assertThat(getJson("/api/v1/admin/loans", admin).toString()).contains(id);
    postJson(endpoint + "/approve", Map.of("reason", ""), admin, 400);
    var decision = Map.of("reason", "Income and repayment capacity verified", "verifiedSalarySlipIds", documentIds(id));
    var results = together(() -> postJson(endpoint + "/approve", decision, admin, 200));
    assertThat(results.get(0).get("PRODUCT_STATUS").asText()).isEqualTo("APPROVED");
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE target_id=? AND operation='APPROVE_LOAN'",
                Integer.class,
                id))
        .isEqualTo(1);
    postJson(endpoint + "/reject", decision, admin, 409);
    assertThat(getJson("/api/v1/admin/loans", admin).toString()).doesNotContain(id);
    long bank =
        db.queryForObject(
            "SELECT id FROM accounts WHERE account_number='NEXA-BANK-FUNDING'", Long.class);
    BigDecimal before = balance(Long.toString(bank));
    db.update("UPDATE accounts SET balance=1 WHERE id=?", bank);
    postJson(root(id) + "/accept", Map.of(), auth, 400);
    assertThat(balance(source)).isEqualByComparingTo("1000");
    assertThat(getJson(root(id) + "/payments", auth).size()).isZero();
    db.update("UPDATE accounts SET balance=? WHERE id=?", before, bank);
    postJson(root(id) + "/accept", Map.of(), auth, 200);
    assertThat(balance(Long.toString(bank)))
        .isEqualByComparingTo(before.subtract(new BigDecimal("12000")));
    var row = getJson(root(id) + "/schedule", auth).get(0);
    postJson(pay(id, row.get("id").asText()), Map.of(), auth, 200);
    assertThat(balance(Long.toString(bank)))
        .isEqualByComparingTo(
            before
                .subtract(new BigDecimal("12000"))
                .add(row.get("principalAmount").decimalValue()));
    var rejected =
        postJson("/api/v1/loans", application("reject-request", 3), auth, 201).get("id").asText();
    postJson(
        "/api/v1/admin/loans/" + rejected + "/reject",
        Map.of("reason", "Existing credit exposure"),
        admin,
        200);
    postJson(root(rejected) + "/accept", Map.of(), auth, 400);
    assertThat(getJson(root(rejected), auth).get("status").asText()).isEqualTo("REJECTED");
    balanced();
  }
}
