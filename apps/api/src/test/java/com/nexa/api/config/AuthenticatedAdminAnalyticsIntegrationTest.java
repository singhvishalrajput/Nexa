package com.nexa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexa.api.NexaApiApplication;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Exercises real authorization and aggregate SQL using an isolated, synthetic H2 schema. */
@SpringBootTest(classes = NexaApiApplication.class, properties = {
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.default_schema=PUBLIC", "spring.jpa.open-in-view=false",
    "spring.jpa.show-sql=false", "logging.level.root=WARN",
    "spring.datasource.hikari.schema=PUBLIC", "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'",
    "spring.sql.init.mode=never", "spring.config.import=", "nexa.onboarding.enabled=false",
    "nexa.onboarding.documents.enabled=false", "nexa.onboarding.documents.root=",
    "nexa.onboarding.identity.key-id=", "nexa.onboarding.identity.encryption-key=",
    "nexa.payouts.account-key=", "nexa.payouts.mode=DISABLED",
    "nexa.payouts.client-id=", "nexa.payouts.client-secret=",
    "nexa.admin.bootstrap.email=", "nexa.admin.bootstrap.password=",
    "nexa.security.jwt.secret=isolated-admin-analytics-secret-with-more-than-32-bytes",
    "nexa.security.jwt.issuer=isolated-admin-analytics", "nexa.ai.enabled=false",
    "nexa.cors.allowed-origins=http://localhost:8000"
})
@ActiveProfiles("integration")
@AutoConfigureMockMvc
@Import(AuthenticatedAdminAnalyticsIntegrationTest.FixedTime.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthenticatedAdminAnalyticsIntegrationTest {
  private static final String API = "/api/v1/admin/analytics";
  private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");
  private static final Instant OLD = Instant.parse("2026-01-01T00:00:00Z");
  private static final Connection KEEP_ALIVE = initialize();
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired AnalyticsClock clock;
  @Autowired com.nexa.api.security.JwtService jwt;
  private long admin;
  private int serial;

  @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> {
      try { return KEEP_ALIVE.getMetaData().getURL() + ";MODE=Oracle;LOCK_TIMEOUT=10000"; }
      catch (Exception error) { throw new IllegalStateException(error); }
    });
    properties.add("spring.datasource.username", () -> "sa");
    properties.add("spring.datasource.password", () -> "");
    properties.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
  }

  @BeforeEach void reset() throws Exception {
    try (Connection connection = Objects.requireNonNull(db.getDataSource()).getConnection()) {
      assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:account-applications-");
    }
    for (String table : List.of("external_transfer_reviews", "external_bank_payees", "bill_payment_attempts",
        "application_events", "opening_cash_receipts", "application_documents", "account_applications",
        "ledger_entries", "journal_entries", "transactions", "customer_credentials")) db.update("DELETE FROM " + table);
    db.update("DELETE FROM accounts WHERE account_category='CUSTOMER'");
    db.update("DELETE FROM customers");
    serial = 0;
    clock.now = NOW;
    admin = customer("ADMIN", "ACTIVE", OLD);
  }

  @AfterAll static void closeKeeper() throws Exception { KEEP_ALIVE.close(); }

  @Test void anonymousStaffAndStaleAdminTokensCannotReadAggregates() throws Exception {
    mvc.perform(get(API)).andExpect(status().isUnauthorized());
    mvc.perform(get(API + "?days=1")).andExpect(status().isUnauthorized());
    for (String role : List.of("CUSTOMER", "SUPPORT_AGENT", "FRAUD_ANALYST")) {
      long person = customer(role, "ACTIVE", OLD);
      for (String query : List.of("", "?days=1")) {
        mvc.perform(get(API + query).header("Authorization", authorization(person, role))).andExpect(status().isForbidden());
        // A formerly administrative JWT does not override the current database role.
        mvc.perform(get(API + query).header("Authorization", authorization(person, "ADMIN"))).andExpect(status().isForbidden());
      }
    }
    db.update("UPDATE customers SET status='LOCKED' WHERE id=?", admin);
    mvc.perform(get(API).header("Authorization", authorization(admin, "ADMIN"))).andExpect(status().isUnauthorized());
    db.update("UPDATE customers SET status='DISABLED' WHERE id=?", admin);
    mvc.perform(get(API).header("Authorization", authorization(admin, "ADMIN"))).andExpect(status().isUnauthorized());
  }

  @Test void emptyDatabaseHasRealZeroesAndThirtyCompleteDailyBucketsByDefault() throws Exception {
    var result = read("");
    assertThat(result.path("generatedAt").asText()).isEqualTo(NOW.toString());
    assertThat(result.path("timezone").asText()).isEqualTo("Asia/Kolkata");
    assertThat(result.path("period").path("days").asInt()).isEqualTo(30);
    assertThat(result.path("period").path("granularity").asText()).isEqualTo("DAY");
    assertThat(result.path("snapshot").path("customers").asLong()).isZero();
    assertThat(result.path("snapshot").path("customerAccounts").asLong()).isZero();
    assertThat(result.path("snapshot").path("activeDepositBalances").size()).isZero();
    assertThat(result.path("activity").path("postedPayments").asLong()).isZero();
    assertThat(result.path("activity").path("daily").size()).isEqualTo(30);
    assertThat(result.path("activity").path("hourly").size()).isZero();
    for (var day : result.path("activity").path("daily")) {
      assertThat(day.path("postedPayments").asLong()).isZero();
      assertThat(day.path("newCustomers").asLong()).isZero();
      assertThat(day.path("paymentAmounts").size()).isZero();
    }
    assertThat(result.toString()).doesNotContain("@", "accountNumber", "identity", "Synthetic", "userId");
  }

  @Test void onlySupportedPeriodChoicesAreAccepted() throws Exception {
    for (int days : List.of(7, 30, 90)) {
      var result = read("?days=" + days);
      assertThat(result.path("activity").path("daily").size()).isEqualTo(days);
      assertThat(result.path("activity").path("hourly").size()).isZero();
      assertThat(result.path("period").path("granularity").asText()).isEqualTo("DAY");
    }
    assertThat(read("?days=1").path("activity").path("hourly").size()).isEqualTo(25);
    for (String days : List.of("0", "2", "-7", "365", "2147483647", "abc", "7.0"))
      mvc.perform(get(API + "?days=" + days).header("Authorization", authorization(admin, "ADMIN")))
          .andExpect(status().isBadRequest());
  }

  @Test void rollingDayIncludesOnlyIntersectingIndiaCalendarHoursWithClippedZeroBuckets() throws Exception {
    var result = read("?days=1");
    var period = result.path("period");
    assertThat(period.path("days").asInt()).isEqualTo(1);
    assertThat(period.path("granularity").asText()).isEqualTo("HOUR");
    assertThat(period.path("startAt").asText()).isEqualTo("2026-09-25T10:00:00Z");
    assertThat(period.path("endAt").asText()).isEqualTo(NOW.toString());
    assertThat(period.path("startDate").asText()).isEqualTo("2026-09-25");
    assertThat(period.path("endDate").asText()).isEqualTo("2026-09-26");
    assertThat(result.path("activity").path("daily").size()).isZero();
    var hours = result.path("activity").path("hourly");
    assertThat(hours.size()).isEqualTo(25);
    assertThat(hours.get(0).path("startAt").asText()).isEqualTo("2026-09-25T10:00:00Z");
    assertThat(hours.get(0).path("endAt").asText()).isEqualTo("2026-09-25T10:30:00Z");
    assertThat(hours.get(24).path("startAt").asText()).isEqualTo("2026-09-26T09:30:00Z");
    assertThat(hours.get(24).path("endAt").asText()).isEqualTo(NOW.toString());
    Instant previousEnd = Instant.parse(period.path("startAt").asText());
    for (var hour : hours) {
      Instant start = Instant.parse(hour.path("startAt").asText());
      Instant end = Instant.parse(hour.path("endAt").asText());
      assertThat(start).isEqualTo(previousEnd);
      assertThat(end).isAfter(start).isBeforeOrEqualTo(NOW);
      assertThat(Duration.between(start, end)).isLessThanOrEqualTo(Duration.ofHours(1));
      assertThat(hour.path("postedPayments").asLong()).isZero();
      assertThat(hour.path("newCustomers").asLong()).isZero();
      assertThat(hour.path("newAccounts").asLong()).isZero();
      assertThat(hour.path("paymentAmounts").size()).isZero();
      previousEnd = end;
    }
    assertThat(previousEnd).isEqualTo(NOW);
    assertThat(read("?days=7").path("snapshot")).isEqualTo(result.path("snapshot"));
  }

  @Test void exactIndiaHourAndMidnightHaveTwentyFourBucketsWithoutAnEmptyFutureHour() throws Exception {
    for (Instant now : List.of(Instant.parse("2026-09-26T10:30:00Z"), Instant.parse("2026-09-26T18:30:00Z"))) {
      clock.now = now;
      var result = read("?days=1");
      var hours = result.path("activity").path("hourly");
      assertThat(hours.size()).isEqualTo(24);
      assertThat(hours.get(0).path("startAt").asText()).isEqualTo(now.minusSeconds(86400).toString());
      assertThat(hours.get(23).path("endAt").asText()).isEqualTo(now.toString());
      for (var hour : hours) assertThat(Duration.between(Instant.parse(hour.path("startAt").asText()),
          Instant.parse(hour.path("endAt").asText()))).isEqualTo(Duration.ofHours(1));
    }
  }

  @Test void hourlyPaymentsRespectRollingCutoffsIndiaMidnightAndCompletionFallback() throws Exception {
    long owner = customer("CUSTOMER", "ACTIVE", OLD);
    long destination = account(owner, "SAVINGS", "ACTIVE", "INR", "1000", OLD);
    Instant start = NOW.minusSeconds(86400);
    payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "100", start.minusNanos(1000));
    payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "1", start);
    payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "2", Instant.parse("2026-09-25T10:29:59.999999Z"));
    payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "3", Instant.parse("2026-09-25T10:30:00Z"));
    payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "4", Instant.parse("2026-09-25T18:29:59.999999Z"));
    payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "5", Instant.parse("2026-09-25T18:30:00Z"));
    String completed = payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "6", NOW.minusNanos(1000));
    db.update("UPDATE transactions SET created_at=? WHERE id=?", Timestamp.from(OLD), completed);
    String fallback = payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "7", NOW.minusSeconds(1800));
    db.update("UPDATE transactions SET completed_at=NULL WHERE id=?", fallback);
    payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "100", NOW);
    payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "100", NOW.plusNanos(1000));
    var activity = read("?days=1").path("activity");
    assertThat(activity.path("postedPayments").asLong()).isEqualTo(7);
    assertMoney(activity.path("paymentAmounts"), "INR", "28.00");
    var hours = activity.path("hourly");
    assertThat(hours.get(0).path("postedPayments").asLong()).isEqualTo(2);
    assertMoney(hours.get(0).path("paymentAmounts"), "INR", "3.00");
    assertMoney(hours.get(1).path("paymentAmounts"), "INR", "3.00");
    assertMoney(hours.get(8).path("paymentAmounts"), "INR", "4.00");
    assertThat(hours.get(9).path("startAt").asText()).isEqualTo("2026-09-25T18:30:00Z");
    assertMoney(hours.get(9).path("paymentAmounts"), "INR", "5.00");
    assertThat(hours.get(24).path("postedPayments").asLong()).isEqualTo(2);
    assertMoney(hours.get(24).path("paymentAmounts"), "INR", "13.00");
    assertMoney(hours.get(2).path("paymentAmounts"), "INR", "0.00");
  }

  @Test void hourlyGrowthUsesTheSameRollingBoundsAndCustomerOwnershipFilters() throws Exception {
    Instant start = NOW.minusSeconds(86400), midnight = Instant.parse("2026-09-25T18:30:00Z");
    long owner = customer("CUSTOMER", "ACTIVE", start);
    customer("CUSTOMER", "DISABLED", midnight);
    customer("CUSTOMER", "ACTIVE", start.minusNanos(1000));
    customer("CUSTOMER", "ACTIVE", NOW);
    customer("ADMIN", "ACTIVE", start);
    account(owner, "SAVINGS", "ACTIVE", "INR", "10", start);
    account(owner, "CARD", "ACTIVE", "INR", "0", midnight);
    account(owner, "CURRENT", "ACTIVE", "INR", "0", NOW);
    account(owner, "CURRENT", "ACTIVE", "INR", "0", start.minusNanos(1000));
    account(admin, "SAVINGS", "ACTIVE", "INR", "0", start);
    var activity = read("?days=1").path("activity");
    assertThat(activity.path("newCustomers").asLong()).isEqualTo(2);
    assertThat(activity.path("newAccounts").asLong()).isEqualTo(2);
    for (int bucket : List.of(0, 9)) {
      assertThat(activity.path("hourly").get(bucket).path("newCustomers").asLong()).isEqualTo(1);
      assertThat(activity.path("hourly").get(bucket).path("newAccounts").asLong()).isEqualTo(1);
    }
    assertThat(activity.path("hourly").get(24).path("newCustomers").asLong()).isZero();
    assertThat(activity.path("hourly").get(24).path("newAccounts").asLong()).isZero();
  }

  @Test void snapshotSeparatesStaffSystemProductsAndInactiveDepositBalances() throws Exception {
    long owner = customer("CUSTOMER", "ACTIVE", OLD);
    long locked = customer("CUSTOMER", "LOCKED", OLD);
    customer("SUPPORT_AGENT", "ACTIVE", OLD);
    long legacy = customer("CUSTOMER", "ACTIVE", OLD);
    db.update("DELETE FROM customer_credentials WHERE user_id=?", userId(legacy));
    db.update("UPDATE customers SET user_id=NULL WHERE id=?", legacy);
    account(owner, "SAVINGS", "ACTIVE", "INR", "100.10", OLD);
    account(owner, "CURRENT", "ACTIVE", "INR", "200.20", OLD);
    account(locked, "SAVINGS", "ACTIVE", "USD", "77.15", OLD);
    account(owner, "SAVINGS", "BLOCKED", "INR", "999.00", OLD);
    account(owner, "SAVINGS", "CLOSED", "INR", "0", OLD);
    account(owner, "LOAN", "ACTIVE", "INR", "800.00", OLD);
    account(owner, "CARD", "ACTIVE", "INR", "400.00", OLD);
    account(admin, "SAVINGS", "ACTIVE", "INR", "50000.00", OLD);
    account(legacy, "SAVINGS", "ACTIVE", "INR", "60000.00", OLD);
    db.update("UPDATE accounts SET balance=999999 WHERE account_category='SYSTEM'");
    var result = read("?days=7").path("snapshot");
    assertThat(result.path("customers").asLong()).isEqualTo(2);
    assertThat(result.path("activeCustomers").asLong()).isEqualTo(1);
    assertThat(result.path("customerAccounts").asLong()).isEqualTo(7);
    assertThat(result.path("activeCustomerAccounts").asLong()).isEqualTo(5);
    assertMoney(result.path("activeDepositBalances"), "INR", "300.30");
    assertMoney(result.path("activeDepositBalances"), "USD", "77.15");
    assertCount(result.path("accountTypes"), "SAVINGS", 4);
    assertCount(result.path("accountTypes"), "LOAN", 1);
    assertCount(result.path("accountStatuses"), "BLOCKED", 1);
    assertThat(read("?days=7").path("activity").path("newCustomers").asLong()).isZero();
  }

  @Test void postedVolumeCountsEachPaymentOnceAndExcludesNonPostingRecordsAndSystemLegs() throws Exception {
    long owner = customer("CUSTOMER", "ACTIVE", OLD);
    long source = account(owner, "SAVINGS", "ACTIVE", "INR", "1000", OLD);
    long destination = account(owner, "CURRENT", "ACTIVE", "INR", "1000", OLD);
    long usd = account(owner, "SAVINGS", "ACTIVE", "USD", "1000", OLD);
    Instant at = NOW.minusSeconds(60);
    String transfer = payment(source, destination, "TRANSFER", "SUCCESS", "POSTED", "10.10", at);
    long journal = db.queryForObject("SELECT id FROM journal_entries WHERE transaction_id=?", Long.class, transfer);
    for (String type : List.of("DEBIT", "CREDIT"))
      db.update("INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at) VALUES(?,?,?,?,?)",
          journal, source, type, new BigDecimal("10.10"), Timestamp.from(at));
    payment(null, source, "DEPOSIT", "SUCCESS", "POSTED", "5.20", at);
    payment(usd, null, "WITHDRAWAL", "SUCCESS", "POSTED", "3.40", at);
    payment(source, destination, "TRANSFER", "SUCCESS", null, "900", at); // Imported history, not journal backed.
    payment(source, destination, "TRANSFER", "FAILED", "POSTED", "900", at);
    payment(source, destination, "TRANSFER", "REVERSED", "REVERSED", "900", at);
    payment(source, destination, "TRANSFER", "SUCCESS", "REVERSED", "900", at);
    payment(source, usd, "TRANSFER", "SUCCESS", "POSTED", "900", at); // Inconsistent currency excluded.
    String conflict = payment(source, destination, "TRANSFER", "SUCCESS", "POSTED", "900", at);
    db.update("UPDATE transactions SET currency_code='USD' WHERE id=?", conflict);
    var systems = db.queryForList("SELECT id FROM accounts WHERE account_category='SYSTEM' ORDER BY id", Long.class);
    payment(systems.get(0), systems.get(1), "TRANSFER", "SUCCESS", "POSTED", "500000", at);
    for (String kind : List.of("BILL", "BENEFICIARY", "ADMIN_EVENT", "PRODUCT_HISTORY", "SIMULATION"))
      db.update("INSERT INTO transactions(id,record_kind,user_id,status,amount,currency_code,created_at) VALUES(?,?,?,'COMPLETED',9999,'INR',?)",
          "noise-" + kind, kind, userId(owner), Timestamp.from(at));
    var activity = read("?days=7").path("activity");
    assertThat(activity.path("postedPayments").asLong()).isEqualTo(3);
    assertMoney(activity.path("paymentAmounts"), "INR", "15.30");
    assertMoney(activity.path("paymentAmounts"), "USD", "3.40");
    assertThat(activity.path("daily").get(6).path("postedPayments").asLong()).isEqualTo(3);
    assertMoney(activity.path("daily").get(0).path("paymentAmounts"), "USD", "0.00");
    var hourly = read("?days=1").path("activity");
    assertThat(hourly.path("postedPayments").asLong()).isEqualTo(3);
    assertThat(hourly.path("paymentAmounts")).isEqualTo(activity.path("paymentAmounts"));
    assertThat(hourly.path("hourly").get(24).path("postedPayments").asLong()).isEqualTo(3);
    assertMoney(hourly.path("hourly").get(24).path("paymentAmounts"), "INR", "15.30");
    assertMoney(hourly.path("hourly").get(24).path("paymentAmounts"), "USD", "3.40");
    assertMoney(hourly.path("hourly").get(0).path("paymentAmounts"), "INR", "0.00");
    assertMoney(hourly.path("hourly").get(0).path("paymentAmounts"), "USD", "0.00");
  }

  @Test void indiaMidnightAndSnapshotCutoffUseUtcBoundsWithCompletionDate() throws Exception {
    long owner = customer("CUSTOMER", "ACTIVE", OLD);
    long destination = account(owner, "SAVINGS", "ACTIVE", "INR", "1000", OLD);
    payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "100", Instant.parse("2026-09-19T18:29:59Z"));
    payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "1", Instant.parse("2026-09-19T18:30:00Z"));
    String beforeMidnight = payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "2", Instant.parse("2026-09-20T18:29:59Z"));
    assertThat(db.queryForObject("SELECT TO_CHAR(completed_at,'YYYY-MM-DD HH24:MI:SS') FROM transactions WHERE id=?",
        String.class, beforeMidnight)).isEqualTo("2026-09-20 18:29:59");
    payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "3", Instant.parse("2026-09-20T18:30:00Z"));
    payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "100", NOW);
    payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "100", NOW.plusSeconds(60));
    String completedToday = payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "4", NOW.minusSeconds(60));
    db.update("UPDATE transactions SET created_at=? WHERE id=?", Timestamp.from(OLD), completedToday);
    var result = read("?days=7");
    assertThat(result.path("period").path("startAt").asText()).isEqualTo("2026-09-19T18:30:00Z");
    assertThat(result.path("period").path("endAt").asText()).isEqualTo(NOW.toString());
    var daily = result.path("activity").path("daily");
    assertThat(daily.get(0).path("date").asText()).isEqualTo("2026-09-20");
    assertThat(daily.get(0).path("postedPayments").asLong()).isEqualTo(2);
    assertThat(daily.get(1).path("postedPayments").asLong()).isEqualTo(1);
    assertThat(daily.get(6).path("postedPayments").asLong()).isEqualTo(1);
    assertThat(result.path("activity").path("postedPayments").asLong()).isEqualTo(4);
    assertMoney(result.path("activity").path("paymentAmounts"), "INR", "10.00");
  }

  @Test void registrationAndAccountGrowthShareTheSameBusinessDateBuckets() throws Exception {
    Instant start = Instant.parse("2026-09-19T18:30:00Z");
    long owner = customer("CUSTOMER", "ACTIVE", start);
    customer("CUSTOMER", "DISABLED", Instant.parse("2026-09-20T18:30:00Z"));
    customer("CUSTOMER", "ACTIVE", start.minusSeconds(1));
    customer("ADMIN", "ACTIVE", start);
    account(owner, "SAVINGS", "ACTIVE", "INR", "10", start);
    account(owner, "CARD", "ACTIVE", "INR", "0", Instant.parse("2026-09-20T18:30:00Z"));
    account(admin, "SAVINGS", "ACTIVE", "INR", "0", start);
    var activity = read("?days=7").path("activity");
    assertThat(activity.path("newCustomers").asLong()).isEqualTo(2);
    assertThat(activity.path("newAccounts").asLong()).isEqualTo(2);
    assertThat(activity.path("daily").get(0).path("newCustomers").asLong()).isEqualTo(1);
    assertThat(activity.path("daily").get(1).path("newAccounts").asLong()).isEqualTo(1);
  }

  @Test void operationalBacklogSeparatesDraftsCustomerCorrectionsAndAdminWork() throws Exception {
    for (String state : List.of("DRAFT", "PENDING_REVIEW", "CHANGES_REQUESTED", "APPROVED_AWAITING_CASH",
        "CASH_RECEIVED", "REFUND_PENDING")) application(state);
    long owner = customer("CUSTOMER", "ACTIVE", OLD);
    long pending = account(owner, "LOAN", "ACTIVE", "INR", "0", OLD);
    db.update("UPDATE accounts SET product_status='PENDING_APPROVAL' WHERE id=?", pending);
    long rejected = account(owner, "LOAN", "ACTIVE", "INR", "0", OLD);
    db.update("UPDATE accounts SET product_status='REJECTED' WHERE id=?", rejected);
    var snapshot = read("?days=7").path("snapshot");
    assertThat(snapshot.path("applicationBacklog").asLong()).isEqualTo(4);
    assertThat(snapshot.path("pendingLoans").asLong()).isEqualTo(1);
    assertCount(snapshot.path("applicationStatuses"), "DRAFT", 1);
    assertCount(snapshot.path("applicationStatuses"), "CHANGES_REQUESTED", 1);
    assertCount(snapshot.path("applicationStatuses"), "OPENED", 0);
  }

  @Test void aggregatedMoneyIsAnExactDecimalStringAndReadsNeverWrite() throws Exception {
    long owner = customer("CUSTOMER", "ACTIVE", OLD);
    long destination = account(owner, "SAVINGS", "ACTIVE", "INR", "900719925474099.99", OLD);
    payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "900719925474099.99", NOW.minusSeconds(1));
    payment(null, destination, "DEPOSIT", "SUCCESS", "POSTED", "0.01", NOW.minusSeconds(1));
    var before = db.queryForList("SELECT id,balance,version FROM accounts ORDER BY id");
    var transactions = db.queryForList("SELECT id,status FROM transactions ORDER BY id");
    var result = read("?days=7");
    assertMoney(result.path("activity").path("paymentAmounts"), "INR", "900719925474100.00");
    assertThat(result.path("activity").path("paymentAmounts").get(0).path("amount").isString()).isTrue();
    assertMoney(read("?days=1").path("activity").path("hourly").get(24).path("paymentAmounts"), "INR", "900719925474100.00");
    assertThat(db.queryForList("SELECT id,balance,version FROM accounts ORDER BY id")).isEqualTo(before);
    assertThat(db.queryForList("SELECT id,status FROM transactions ORDER BY id")).isEqualTo(transactions);
  }

  private JsonNode read(String query) throws Exception {
    return json.readTree(mvc.perform(get(API + query).header("Authorization", authorization(admin, "ADMIN")))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }
  private String authorization(long person, String role) {
    return "Bearer " + jwt.issue(userId(person), "fixture@example.test", role).value();
  }
  private String userId(long person) { return db.queryForObject("SELECT user_id FROM customers WHERE id=?", String.class, person); }
  private long customer(String role, String status, Instant created) {
    String id = "analytics-user-" + (++serial);
    db.update("INSERT INTO customers(user_id,full_name,email,status,role,created_at,updated_at) VALUES(?,? ,?,?,?,?,?)",
        id, "Synthetic Person", id + "@example.test", status, role, Timestamp.from(created), Timestamp.from(created));
    db.update("INSERT INTO customer_credentials(id,user_id,credential_type,password_hash) VALUES(?,?,'PASSWORD','synthetic-fixture-hash')",
        id, id);
    return db.queryForObject("SELECT id FROM customers WHERE user_id=?", Long.class, id);
  }
  private long account(long owner, String type, String status, String currency, String balance, Instant created) {
    String number = "analytics-account-" + (++serial);
    db.update("INSERT INTO accounts(account_number,customer_id,account_name,account_type,account_category,"
        + "currency_code,balance,status,principal_amount,interest_rate,created_at,updated_at) VALUES(?,?,?,?,'CUSTOMER',?,?,?,1000,1,?,?)",
        number, owner, "Synthetic account", type, currency, new BigDecimal(balance), status, Timestamp.from(created), Timestamp.from(created));
    return db.queryForObject("SELECT id FROM accounts WHERE account_number=?", Long.class, number);
  }
  private String payment(Long source, Long destination, String type, String status, String journalStatus, String amount, Instant at) {
    String id = "TX-" + UUID.randomUUID();
    db.update("INSERT INTO transactions(id,record_kind,transaction_type,source_account_id,destination_account_id,"
        + "status,amount,created_at,completed_at) VALUES(?,'PAYMENT',?,?,?,?,?,?,?)",
        id, type, source, destination, status, new BigDecimal(amount), Timestamp.from(at), Timestamp.from(at));
    if (journalStatus != null) db.update("INSERT INTO journal_entries(transaction_id,entry_reference,entry_type,status,created_at)"
        + " VALUES(?,?,'TRANSACTION',?,?)", id, id, journalStatus, Timestamp.from(at));
    return id;
  }
  private void application(String state) {
    long owner = customer("CUSTOMER", "ACTIVE", OLD);
    String id = UUID.randomUUID().toString();
    boolean draft = "DRAFT".equals(state);
    boolean approved = List.of("APPROVED_AWAITING_CASH", "CASH_RECEIVED", "REFUND_PENDING").contains(state);
    var created = Instant.parse("2026-09-19T10:00:00Z").atOffset(ZoneOffset.UTC);
    db.update("INSERT INTO account_applications(id,customer_id,request_key,full_name,email,date_of_birth,business_date,"
        + "requested_amount,consent_notice_version,consented_at,created_at,updated_at,identity_type,identity_last4,"
        + "status,review_decision,submitted_at,reviewed_at,reviewed_by,review_reason)"
        + " VALUES(?,?,?,'Synthetic Applicant','applicant@example.test',DATE '1990-01-01',DATE '2026-09-19',1000,'in-person-identity-v1',"
        + "?,?,?,'AADHAAR','1234',?,?,?,?,?,?)", id, owner, id, created, created, created, state,
        approved ? "APPROVED" : "PENDING", draft ? null : created, approved ? created : null,
        approved ? userId(admin) : null, approved ? "Synthetic review" : null);
  }
  private static void assertMoney(JsonNode values, String currency, String expected) {
    for (var value : values) if (value.path("currencyCode").asText().equals(currency)) {
      assertThat(value.path("amount").asText()).isEqualTo(expected); return;
    }
    throw new AssertionError("Missing currency " + currency);
  }
  private static void assertCount(JsonNode values, String key, long expected) {
    for (var value : values) if (value.path("key").asText().equals(key)) {
      assertThat(value.path("count").asLong()).isEqualTo(expected); return;
    }
    throw new AssertionError("Missing count " + key);
  }
  private static Connection initialize() {
    try { return AccountApplicationTestDatabase.initializedExternalTransferDatabase(); }
    catch (Exception error) { throw new ExceptionInInitializerError(error); }
  }
  static class AnalyticsClock extends Clock {
    Instant now = NOW;
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
    @Override public Instant instant() { return now; }
  }
  @TestConfiguration static class FixedTime {
    @Bean @Primary AnalyticsClock analyticsClock() { return new AnalyticsClock(); }
  }
}
