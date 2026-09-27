package com.nexa.api.config;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.nexa.api.NexaApiApplication;
import com.nexa.api.service.ScheduledPaymentService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes=NexaApiApplication.class,properties={
    "spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.default_schema=PUBLIC",
    "spring.jpa.open-in-view=false","spring.jpa.show-sql=false","logging.level.root=WARN",
    "spring.datasource.hikari.schema=PUBLIC","spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'",
    "spring.sql.init.mode=never","spring.config.import=","nexa.onboarding.enabled=false",
    "nexa.onboarding.documents.enabled=false","nexa.onboarding.documents.root=",
    "nexa.onboarding.identity.key-id=","nexa.onboarding.identity.encryption-key=",
    "nexa.payouts.account-key=","nexa.payouts.mode=DISABLED","nexa.payouts.client-id=","nexa.payouts.client-secret=",
    "nexa.admin.bootstrap.email=","nexa.admin.bootstrap.password=","nexa.scheduled-payments.enabled=true",
    "nexa.security.jwt.secret=isolated-scheduled-payment-secret-with-more-than-32-bytes",
    "nexa.security.jwt.issuer=isolated-scheduled-payment","nexa.ai.enabled=false","nexa.cors.allowed-origins=http://localhost:8000"
})
@ActiveProfiles("integration")
@AutoConfigureMockMvc
@Import(AuthenticatedScheduledPaymentIntegrationTest.TimeConfiguration.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class AuthenticatedScheduledPaymentIntegrationTest {
  private static final String API="/api/v1/scheduled-payments";
  private static final Instant NOW=Instant.parse("2026-09-26T10:00:00Z");
  private static final Connection KEEP_ALIVE=initialize();
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired ScheduledPaymentService schedules;
  @Autowired MutableClock clock;
  @Autowired com.nexa.api.security.JwtService jwt;
  private long source,destination,other;
  private String payee;

  @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url",()->{try{return KEEP_ALIVE.getMetaData().getURL();}catch(Exception error){throw new IllegalStateException(error);}});
    properties.add("spring.datasource.username",()->"sa");properties.add("spring.datasource.password",()->"");
    properties.add("spring.datasource.driver-class-name",()->"org.h2.Driver");
  }
  @BeforeEach void reset() throws Exception {
    try(Connection connection=Objects.requireNonNull(db.getDataSource()).getConnection()) {assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:account-applications-");}
    for(String table:List.of("authorized_scheduled_payments","external_transfer_reviews","external_bank_payees","bill_payment_attempts",
        "ledger_entries","journal_entries","transactions","customer_credentials")) db.update("DELETE FROM "+table);
    db.update("DELETE FROM accounts WHERE account_category='CUSTOMER'");db.update("DELETE FROM customers");clock.now=NOW;
    customer("schedule-owner","CUSTOMER");customer("schedule-recipient","CUSTOMER");customer("schedule-other","CUSTOMER");customer("schedule-staff","ADMIN");
    source=account("schedule-owner","900000000001","1000.00");destination=account("schedule-recipient","900000000002","100.00");other=account("schedule-other","900000000003","1000.00");
    payee=write("/api/v1/beneficiaries","schedule-owner",Map.of("displayName","Synthetic Recipient","accountNumber","900000000002"),200).path("id").asText();
  }
  @AfterAll static void close() throws Exception {KEEP_ALIVE.close();}

  @Test void explicitAuthorizationDefersAnExactlyOnceBalancedPostingUntilIndiaMidnight() throws Exception {
    var body=body();var ready=write(API+"/prepare","schedule-owner",body,200);String id=ready.path("id").asText();
    assertThat(ready.path("managed").asBoolean()).isTrue();assertThat(ready.path("status").asText()).isEqualTo("READY");
    assertThat(ready.toString()).doesNotContain("900000000001","900000000002");
    assertThat(write(API+"/prepare","schedule-owner",body,200).path("id").asText()).isEqualTo(id);
    write(API+"/"+id+"/confirm","schedule-owner",Map.of("authorizationAccepted",false),400);
    assertThat(schedules.dueIds()).isEmpty();assertBalances("1000.00","100.00",0);
    assertThat(confirm(id).path("status").asText()).isEqualTo("SCHEDULED");assertThat(confirm(id).path("status").asText()).isEqualTo("SCHEDULED");
    clock.now=Instant.parse("2026-09-26T18:29:59Z");schedules.executeDue(id);assertBalances("1000.00","100.00",0);
    clock.now=Instant.parse("2026-09-26T18:30:00Z");assertThat(schedules.dueIds()).containsExactly(id);
    schedules.executeDue(id);schedules.executeDue(id);
    assertBalances("875.50","224.50",1);
    var completed=read(API+"/"+id,"schedule-owner");assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
    String reference=completed.path("reference").asText();assertThat(reference).startsWith("TX-");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM ledger_entries l JOIN journal_entries j ON j.id=l.journal_entry_id WHERE j.transaction_id=?",Long.class,reference)).isEqualTo(2);
    assertThat(db.queryForObject("SELECT SUM(CASE WHEN entry_type='DEBIT' THEN amount ELSE -amount END) FROM ledger_entries",BigDecimal.class)).isEqualByComparingTo("0");
    assertThat(write(API+"/"+id+"/cancel","schedule-owner",Map.of(),200).path("status").asText()).isEqualTo("COMPLETED");
    assertThat(write(API+"/prepare","schedule-owner",body,200).path("status").asText()).isEqualTo("COMPLETED");
    var changed=new LinkedHashMap<>(body);changed.put("amount","125.00");write(API+"/prepare","schedule-owner",changed,409);
  }

  @Test void concurrentWorkersAndConcurrentCancellationCannotPostTwice() throws Exception {
    String id=scheduled();clock.now=NOW.plusSeconds(86400);
    var pool=Executors.newFixedThreadPool(3);var start=new CountDownLatch(1);
    try {
      var one=pool.submit(()->{start.await();schedules.executeDue(id);return null;});
      var two=pool.submit(()->{start.await();schedules.executeDue(id);return null;});
      var cancel=pool.submit(()->{start.await();return write(API+"/"+id+"/cancel","schedule-owner",Map.of(),200);});
      start.countDown();one.get(15,TimeUnit.SECONDS);two.get(15,TimeUnit.SECONDS);cancel.get(15,TimeUnit.SECONDS);
    } finally {pool.shutdownNow();}
    String state=read(API+"/"+id,"schedule-owner").path("status").asText();
    assertThat(state).isIn("COMPLETED","CANCELLED");
    if("COMPLETED".equals(state)) assertBalances("875.50","224.50",1);else assertBalances("1000.00","100.00",0);
  }

  @Test void insufficientFundsFailOnlyAtExecutionWithoutAutomaticDebitRetries() throws Exception {
    db.update("UPDATE accounts SET balance=0 WHERE id=?",source);
    String id=scheduled();assertBalances("0.00","100.00",0);
    clock.now=NOW.plusSeconds(86400);schedules.executeDue(id);
    var failed=read(API+"/"+id,"schedule-owner");assertThat(failed.path("status").asText()).isEqualTo("FAILED");
    assertThat(failed.path("failureReason").asText()).contains("Insufficient funds");assertBalances("0.00","100.00",0);
    db.update("UPDATE accounts SET balance=500 WHERE id=?",source);schedules.executeDue(id);assertBalances("500.00","100.00",0);
  }

  @Test void changedPayeeAccountOrRevokedCustomerStopsAuthorizedPayment() throws Exception {
    String id=scheduled();db.update("UPDATE transactions SET destination_account_id=? WHERE id=?",other,payee);
    clock.now=NOW.plusSeconds(86400);schedules.executeDue(id);assertThat(read(API+"/"+id,"schedule-owner").path("status").asText()).isEqualTo("FAILED");assertBalances("1000.00","100.00",0);
    db.update("UPDATE transactions SET destination_account_id=? WHERE id=?",destination,payee);clock.now=NOW;
    String next=scheduled();db.update("UPDATE customers SET status='LOCKED' WHERE user_id='schedule-owner'");
    clock.now=NOW.plusSeconds(86400);schedules.executeDue(next);
    assertThat(db.queryForObject("SELECT status FROM authorized_scheduled_payments WHERE id=?",String.class,next)).isEqualTo("FAILED");assertBalances("1000.00","100.00",0);
  }

  @Test void inactiveAccountsAndChangedReviewedRecipientFailWithoutPosting() throws Exception {
    String id=scheduled();db.update("UPDATE accounts SET status='BLOCKED' WHERE id=?",destination);clock.now=NOW.plusSeconds(86400);schedules.executeDue(id);
    assertThat(read(API+"/"+id,"schedule-owner").path("status").asText()).isEqualTo("FAILED");assertBalances("1000.00","100.00",0);
    db.update("UPDATE accounts SET status='ACTIVE' WHERE id=?",destination);clock.now=NOW;String next=prepare();
    db.update("UPDATE customers SET full_name='Changed Recipient' WHERE user_id='schedule-recipient'");
    assertThat(confirm(next).path("status").asText()).isEqualTo("FAILED");assertBalances("1000.00","100.00",0);
  }

  @Test void cancellationAndReviewExpiryNeverBecomeAuthorizedPostings() throws Exception {
    String ready=prepare();assertThat(write(API+"/"+ready+"/cancel","schedule-owner",Map.of(),200).path("status").asText()).isEqualTo("CANCELLED");
    String expires=prepare();assertThat(read(API+"?status=READY","schedule-owner").get(0).path("id").asText()).isEqualTo(expires);
    clock.now=NOW.plusSeconds(300);
    assertThat(read(API+"?status=READY","schedule-owner").size()).isZero();
    var expired=read(API+"?status=EXPIRED","schedule-owner");assertThat(expired.size()).isEqualTo(1);
    assertThat(expired.get(0).path("id").asText()).isEqualTo(expires);assertThat(expired.get(0).path("status").asText()).isEqualTo("EXPIRED");
    assertThat(db.queryForObject("SELECT status FROM authorized_scheduled_payments WHERE id=?",String.class,expires)).isEqualTo("READY");
    assertThat(confirm(expires).path("status").asText()).isEqualTo("EXPIRED");
    clock.now=NOW;String scheduled=scheduled();assertThat(write(API+"/"+scheduled+"/cancel","schedule-owner",Map.of(),200).path("status").asText()).isEqualTo("CANCELLED");
    clock.now=NOW.plusSeconds(86400);for(String id:List.of(ready,expires,scheduled))schedules.executeDue(id);assertBalances("1000.00","100.00",0);
  }

  @Test void legacySchedulesStayVisibleButNeverExecuteOrAcquireAuthorization() throws Exception {
    db.update("INSERT INTO transactions(id,record_kind,user_id,source_account_id,destination_account_id,display_name,amount,currency_code,due_at,status,created_at)"
        +" VALUES('legacy-schedule','SCHEDULED_PAYMENT','schedule-owner',?,?,'Historical reminder',999,'INR','2026-09-01','SCHEDULED',?)",source,destination,Timestamp.from(NOW.minusSeconds(86400)));
    var legacy=read(API+"/legacy-schedule","schedule-owner");assertThat(legacy.path("managed").asBoolean()).isFalse();
    assertThat(legacy.path("failureReason").asText()).contains("not authorized");
    String id=scheduled();clock.now=NOW.plusSeconds(86400);assertThat(schedules.dueIds()).containsExactly(id);schedules.executeDue("legacy-schedule");
    assertBalances("1000.00","100.00",0);write(API+"/legacy-schedule/confirm","schedule-owner",Map.of("authorizationAccepted",true),404);
    var list=read(API+"?page=0&size=30","schedule-owner");assertThat(list.size()).isEqualTo(2);
    assertThat(read(API+"?page=1&size=1","schedule-owner").size()).isEqualTo(1);
    assertThat(read(API+"?status=CANCELLED","schedule-owner").size()).isZero();
  }

  @Test void postingFailureRollsBackBalancesAndLeavesOneSafeDurableRetry() throws Exception {
    String id=scheduled();clock.now=NOW.plusSeconds(86400);
    db.execute("ALTER TABLE authorized_scheduled_payments ADD CONSTRAINT fixture_block_completion CHECK(status<>'COMPLETED')");
    try {assertThatThrownBy(()->schedules.executeDue(id)).isInstanceOf(RuntimeException.class);assertBalances("1000.00","100.00",0);
      assertThat(db.queryForObject("SELECT status FROM authorized_scheduled_payments WHERE id=?",String.class,id)).isEqualTo("SCHEDULED");
    } finally {db.execute("ALTER TABLE authorized_scheduled_payments DROP CONSTRAINT fixture_block_completion");}
    schedules.executeDue(id);assertBalances("875.50","224.50",1);
  }

  @Test void authorizationOwnershipDatesCurrencyAndAmountAreCheckedBeforeCreation() throws Exception {
    mvc.perform(get(API)).andExpect(status().isUnauthorized());
    mvc.perform(get(API).header("Authorization",authorization("schedule-staff","ADMIN"))).andExpect(status().isForbidden());
    String id=prepare();mvc.perform(get(API+"/"+id).header("Authorization",authorization("schedule-other","CUSTOMER"))).andExpect(status().isNotFound());
    for(String operation:List.of("confirm","cancel"))write(API+"/"+id+"/"+operation,"schedule-other",Map.of("authorizationAccepted",true),404);
    for(String date:List.of("2026-09-26","2026-09-25","2027-09-27","2027-02-29","bad")) {var body=body();body.put("dueAt",date);write(API+"/prepare","schedule-owner",body,400);}
    for(String amount:List.of("0","-1","1.001","10000000000000","1E+2147483647")) {var body=body();body.put("amount",amount);write(API+"/prepare","schedule-owner",body,400);}
    var another=body();another.put("sourceAccountId",Long.toString(other));write(API+"/prepare","schedule-owner",another,400);
    write(API+"/prepare","schedule-other",body(),400);
    db.update("UPDATE accounts SET currency_code='USD' WHERE id=?",source);write(API+"/prepare","schedule-owner",body(),400);
    assertBalances("1000.00","100.00",0);
    assertThat(read(API+"/requirements","schedule-owner").path("earliestDueDate").asText()).isEqualTo("2026-09-27");
  }

  private Map<String,Object> body() {var value=new LinkedHashMap<String,Object>();value.put("requestKey",UUID.randomUUID().toString());value.put("sourceAccountId",Long.toString(source));value.put("payeeId",payee);value.put("amount","124.50");value.put("dueAt","2026-09-27");return value;}
  private String prepare() throws Exception {return write(API+"/prepare","schedule-owner",body(),200).path("id").asText();}
  private String scheduled() throws Exception {String id=prepare();assertThat(confirm(id).path("status").asText()).isEqualTo("SCHEDULED");return id;}
  private JsonNode confirm(String id) throws Exception {return write(API+"/"+id+"/confirm","schedule-owner",Map.of("authorizationAccepted",true),200);}
  private String authorization(String owner,String role) {return "Bearer "+jwt.issue(owner,owner+"@example.test",role).value();}
  private JsonNode read(String path,String owner) throws Exception {return json.readTree(mvc.perform(get(path).header("Authorization",authorization(owner,"CUSTOMER"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
  private JsonNode write(String path,String owner,Object value,int expected) throws Exception {return json.readTree(mvc.perform(post(path).header("Authorization",authorization(owner,"CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(value))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());}
  private void customer(String owner,String role) {db.update("INSERT INTO customers(user_id,full_name,email,status,role,created_at,updated_at) VALUES(?,? ,?,'ACTIVE',?,?,?)",owner,owner.equals("schedule-recipient")?"Synthetic Recipient":"Synthetic Customer",owner+"@example.test",role,Timestamp.from(NOW),Timestamp.from(NOW));db.update("INSERT INTO customer_credentials(id,user_id,credential_type,password_hash) VALUES(?,?,'PASSWORD','synthetic-fixture-hash')",owner,owner);}
  private long account(String owner,String number,String balance) {long customer=db.queryForObject("SELECT id FROM customers WHERE user_id=?",Long.class,owner);db.update("INSERT INTO accounts(account_number,customer_id,account_name,account_type,account_category,currency_code,balance,status,created_at,updated_at) VALUES(?,?,'Synthetic account','CURRENT','CUSTOMER','INR',?,'ACTIVE',?,?)",number,customer,new BigDecimal(balance),Timestamp.from(NOW),Timestamp.from(NOW));return db.queryForObject("SELECT id FROM accounts WHERE account_number=?",Long.class,number);}
  private void assertBalances(String sourceBalance,String destinationBalance,long payments) {assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?",BigDecimal.class,source)).isEqualByComparingTo(sourceBalance);assertThat(db.queryForObject("SELECT balance FROM accounts WHERE id=?",BigDecimal.class,destination)).isEqualByComparingTo(destinationBalance);assertThat(db.queryForObject("SELECT COUNT(*) FROM transactions WHERE record_kind='PAYMENT'",Long.class)).isEqualTo(payments);assertThat(db.queryForObject("SELECT COUNT(*) FROM journal_entries",Long.class)).isEqualTo(payments);}
  private static Connection initialize() {try {Connection connection=AccountApplicationTestDatabase.initializedExternalTransferDatabase();ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V30__authorized_scheduled_payments.sql"));return connection;}catch(Exception error){throw new ExceptionInInitializerError(error);}}
  public static class MutableClock extends Clock {volatile Instant now=NOW;@Override public ZoneId getZone(){return ZoneOffset.UTC;}@Override public Clock withZone(ZoneId zone){return this;}@Override public Instant instant(){return now;}}
  @TestConfiguration static class TimeConfiguration {@Bean @Primary MutableClock scheduleClock(){return new MutableClock();}}
}
