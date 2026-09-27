package com.nexa.api.banking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexa.api.config.AccountApplicationTestDatabase;
import com.nexa.api.controller.BillController;
import com.nexa.api.exep.ApiExceptionHandler;
import com.nexa.api.repository.JdbcBankingProductRepository;
import com.nexa.api.service.BillQueryService;
import com.nexa.api.service.BusinessDateResolver;
import com.nexa.api.service.PaymentItemService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

/** Real bill SQL against the constrained Oracle-mode H2 fixture; no live database access. */
class BillCreationIntegrationTest {
  private Connection connection;
  private JdbcTemplate db;
  private MockMvc mvc;
  private final AtomicReference<String> owner = new AtomicReference<>("bill-owner");
  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void setUp() throws Exception {
    connection = AccountApplicationTestDatabase.initializedDatabase();
    db = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    db.update("INSERT INTO customers(id,user_id,full_name,email,created_at,updated_at)"
        + " VALUES(101,'bill-owner','Bill owner','bill-owner@example.test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    db.update("INSERT INTO customers(id,user_id,full_name,email,created_at,updated_at)"
        + " VALUES(102,'other-owner','Other owner','other-owner@example.test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    configureClock("2026-09-25T18:30:01Z");
  }

  private void configureClock(String instant) {
    Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    var dates = new BusinessDateResolver(clock, ZoneId.of("Asia/Kolkata"));
    var repository = new JdbcBankingProductRepository(db, json, clock,
        dates);
    mvc = MockMvcBuilders.standaloneSetup(new BillController(
            new BillQueryService(repository, owner::get), new PaymentItemService(db, owner::get, clock, dates)))
        .setControllerAdvice(new ApiExceptionHandler()).build();
  }

  @AfterEach
  void closeDatabase() throws Exception {
    if (connection != null) connection.close();
  }

  @Test
  void createsAndReadsAnOwnedBillWithoutAnAccountOrMoneyMovement() throws Exception {
    mvc.perform(post("/api/v1/bills").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(validBill())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isString())
        .andExpect(jsonPath("$.billerName").value("City Electricity"))
        .andExpect(jsonPath("$.amount").value("1000.50"))
        .andExpect(jsonPath("$.minimumAmount").value("250.25"))
        .andExpect(jsonPath("$.dueAt").value("2026-10-05"))
        .andExpect(jsonPath("$.category").value("Utilities"))
        .andExpect(jsonPath("$.status").value("UPCOMING"))
        .andExpect(jsonPath("$.paidAmount").value("0.00"))
        .andExpect(jsonPath("$.outstandingAmount").value("1000.50"))
        .andExpect(jsonPath("$.customerNumberMasked").value("•••• 6789"))
        .andExpect(jsonPath("$.payeeId").isEmpty())
        .andExpect(jsonPath("$.recipientName").isEmpty())
        .andExpect(jsonPath("$.DESTINATION_MASKED").doesNotExist());
    String id = billId();
    mvc.perform(get("/api/v1/bills")).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(id));
    mvc.perform(get("/api/v1/bills/{id}", id)).andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.accountId").isEmpty());
    mvc.perform(get("/api/v1/bills/{id}/payments", id)).andExpect(status().isOk())
        .andExpect(content().json("[]"));
    assertThat(db.queryForObject("SELECT COUNT(*) FROM accounts WHERE customer_id=101", Integer.class)).isZero();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM transactions WHERE record_kind='PAYMENT'", Integer.class)).isZero();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM journal_entries", Integer.class)).isZero();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isZero();
    assertThat(db.queryForObject("SELECT SUM(balance) FROM accounts", BigDecimal.class)).isEqualByComparingTo("0");
  }

  @Test
  void createsBillBoundToOwnedPayeeWithActualRecipientIdentityWithoutMovingMoney() throws Exception {
    paymentAccounts();
    savedPayee("bill-recipient", "bill-owner", 9002L);
    var accountsBefore = db.queryForList("SELECT id,balance,version FROM accounts ORDER BY id");
    var request = validBill();
    request.put("payeeId", "bill-recipient");
    mvc.perform(post("/api/v1/bills").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billerName").value("City Electricity"))
        .andExpect(jsonPath("$.payeeId").value("bill-recipient"))
        .andExpect(jsonPath("$.recipientName").value("Other owner"))
        .andExpect(jsonPath("$.recipientAccountMasked").value("•••• 9012"))
        .andExpect(jsonPath("$.customerNumberMasked").value("•••• 6789"))
        .andExpect(jsonPath("$.paidAmount").value("0.00"))
        .andExpect(jsonPath("$.status").value("UPCOMING"));
    assertThat(db.queryForObject("SELECT destination_account_id FROM transactions WHERE id=?",
        Long.class, billId())).isEqualTo(9002L);
    assertThat(db.queryForObject("SELECT target_id FROM transactions WHERE id=?", String.class, billId()))
        .isEqualTo("bill-recipient");
    assertThat(db.queryForList("SELECT id,balance,version FROM accounts ORDER BY id")).isEqualTo(accountsBefore);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM transactions WHERE record_kind='PAYMENT'", Integer.class)).isZero();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM journal_entries", Integer.class)).isZero();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isZero();
  }

  @Test
  void rejectsForeignExternalAndUnlinkedPayeesWithoutCreatingBills() throws Exception {
    paymentAccounts();
    savedPayee("foreign-recipient", "other-owner", 9002L);
    savedPayee("unlinked-recipient", "bill-owner", null);
    invalid("payeeId", "foreign-recipient", "Choose an active saved payee");
    invalid("payeeId", "unlinked-recipient", "Choose an active saved payee");
    invalid("payeeId", "EP-00000000-0000-0000-0000-000000000000", "Choose a saved Nexa payee");
    invalid("payeeId", "missing-recipient", "Choose an active saved payee");
    invalid("payeeId", " ", "Choose a saved Nexa payee");
    invalid("payeeId", "b".repeat(41), "Choose a saved Nexa payee");
    assertNoBillOrPosting();
  }

  @Test
  void rejectsSuspendedPayeesAndTamperedDestinationBindings() throws Exception {
    paymentAccounts();
    savedPayee("bill-recipient", "bill-owner", 9002L);
    db.update("UPDATE transactions SET status='SUSPENDED' WHERE id='bill-recipient'");
    invalid("payeeId", "bill-recipient", "Choose an active saved payee");
    db.update("UPDATE transactions SET status='ACTIVE',destination_hash=? WHERE id='bill-recipient'", "0".repeat(64));
    invalid("payeeId", "bill-recipient", "This payee's Nexa account needs verification");
    assertNoBillOrPosting();
  }

  @Test
  void rejectsBlockedClosedNonDepositAndNonRupeeRecipientAccounts() throws Exception {
    paymentAccounts();
    savedPayee("bill-recipient", "bill-owner", 9002L);
    for (String state : new String[] {"BLOCKED", "CLOSED"}) {
      db.update("UPDATE accounts SET status=? WHERE id=9002", state);
      invalid("payeeId", "bill-recipient", "The bill recipient must have an active Nexa INR");
    }
    db.update("UPDATE accounts SET status='ACTIVE',currency_code='USD' WHERE id=9002");
    invalid("payeeId", "bill-recipient", "The bill recipient must have an active Nexa INR");
    db.update("UPDATE accounts SET currency_code='INR',account_type='CARD' WHERE id=9002");
    invalid("payeeId", "bill-recipient", "The bill recipient must have an active Nexa INR");
    assertNoBillOrPosting();
  }

  @Test
  void anotherCustomerCannotReadTheBillAndManualStatusChangesAreRejectedForTheOwner() throws Exception {
    create(validBill());
    String id = billId();
    owner.set("other-owner");
    mvc.perform(get("/api/v1/bills")).andExpect(status().isOk()).andExpect(content().json("[]"));
    mvc.perform(get("/api/v1/bills/{id}", id)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/bills/{id}/payments", id)).andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/bills/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"PAID\"}")).andExpect(status().isNotFound());
    owner.set("bill-owner");
    mvc.perform(get("/api/v1/bills/{id}", id)).andExpect(jsonPath("$.status").value("UPCOMING"));
    for (String state : new String[] {"PAID", "FAILED", "DUE", "OVERDUE", "UPCOMING", "invalid"}) {
      mvc.perform(post("/api/v1/bills/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
              .content(json.writeValueAsString(Map.of("status", state))))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.detail").value("Bill status is managed automatically from its due date and completed payments."));
    }
    mvc.perform(get("/api/v1/bills?status=UPCOMING")).andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get("/api/v1/bills?status=PAID")).andExpect(content().json("[]"));
    assertThat(db.queryForObject("SELECT status FROM transactions WHERE id=?", String.class, id)).isEqualTo("UPCOMING");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM journal_entries", Integer.class)).isZero();
  }

  @Test
  void existingAccountLinkedBillsKeepTheirAccountOwnershipCheck() throws Exception {
    db.update("INSERT INTO accounts(id,account_number,customer_id,account_name,account_type,account_category,"
        + "balance,status,created_at,updated_at) VALUES(9001,'BILL-OWNED-ACCOUNT',101,'Owned','SAVINGS',"
        + "'CUSTOMER',0,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    db.update("INSERT INTO accounts(id,account_number,customer_id,account_name,account_type,account_category,"
        + "balance,status,created_at,updated_at) VALUES(9002,'BILL-OTHER-ACCOUNT',102,'Other','SAVINGS',"
        + "'CUSTOMER',0,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    db.update("INSERT INTO transactions(id,record_kind,user_id,source_account_id,status,display_name,amount)"
        + " VALUES('old-bill','BILL','bill-owner',9001,'DUE','Older bill',100)");
    db.update("INSERT INTO transactions(id,record_kind,user_id,source_account_id,status,display_name,amount)"
        + " VALUES('mismatched-owner','BILL','bill-owner',9002,'DUE','Inconsistent ownership',100)");
    mvc.perform(get("/api/v1/bills")).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value("old-bill"));
    mvc.perform(get("/api/v1/bills/old-bill")).andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value("9001"));
    mvc.perform(get("/api/v1/bills/mismatched-owner")).andExpect(status().isNotFound());
  }

  @Test
  void missingOrOutOfRangeFieldsReturnHelpfulBadRequestsWithoutWritingRows() throws Exception {
    invalid("billerName", null, "Biller name");
    invalid("billerName", "   ", "Biller name");
    invalid("billerName", "a".repeat(161), "Biller name");
    invalid("billerName", "अ".repeat(100), "Biller name");
    invalid("category", " ", "Category");
    invalid("category", "a".repeat(81), "Category");
    invalid("category", "अ".repeat(30), "Category");
    invalid("customerNumber", null, "Customer number");
    invalid("customerNumber", "1".repeat(41), "Customer number");
    invalid("amount", null, "Amount");
    invalid("amount", "0", "Amount");
    invalid("amount", "-1", "Amount");
    invalid("amount", "1.001", "Amount");
    invalid("amount", "10000000000000.00", "Amount");
    invalid("amount", "100000000000000000", "Amount");
    invalid("minimumAmount", "-0.01", "Minimum amount");
    invalid("minimumAmount", "1000.51", "Minimum amount");
    invalid("minimumAmount", "0.001", "Minimum amount");
    invalid("dueAt", null, "Enter a valid due date");
    invalid("dueAt", "0000-01-01", "Enter a valid due date");
    mvc.perform(post("/api/v1/bills").contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":\"not-money\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    assertThat(db.queryForObject("SELECT COUNT(*) FROM transactions", Integer.class)).isZero();
  }

  @Test
  void payableBoundaryAmountsAndCustomerNumbersAreAcceptedAndMinimumIsOptional() throws Exception {
    var bill = validBill();
    bill.put("amount", "9999999999999.99");
    bill.put("customerNumber", "1".repeat(40));
    bill.remove("minimumAmount");
    create(bill);
    assertThat(db.queryForObject("SELECT amount FROM transactions WHERE id=?", BigDecimal.class, billId()))
        .isEqualByComparingTo("9999999999999.99");
    mvc.perform(get("/api/v1/bills/{id}", billId())).andExpect(status().isOk())
        .andExpect(jsonPath("$.minimumAmount").isEmpty());
  }

  @Test
  void minimumCannotExceedSupportedPaymentCapAndExistingLargeBillsRemainReadable() throws Exception {
    var request = validBill();
    request.put("amount", "9999999999999.99");
    request.put("minimumAmount", "10000000000000.00");
    mvc.perform(post("/api/v1/bills").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(startsWith("Minimum amount")));
    assertNoBillOrPosting();
    request.put("minimumAmount", "9999999999999.99");
    create(request);
    mvc.perform(get("/api/v1/bills/{id}", billId()))
        .andExpect(jsonPath("$.minimumAmount").value("9999999999999.99"));
    db.update("UPDATE transactions SET amount=99999999999999999.99 WHERE id=?", billId());
    mvc.perform(get("/api/v1/bills/{id}", billId())).andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value("99999999999999999.99"));
  }

  @Test
  void dueDatesAndStatusFiltersRollOverAtBusinessMidnightBeforePagination() throws Exception {
    configureClock("2026-09-25T18:29:59Z"); // 23:59:59 in Asia/Kolkata; UTC is still September 25.
    String[] dates = {"2026-09-24", "2026-09-25", "2026-09-26"};
    String[] initialStatuses = {"OVERDUE", "DUE", "UPCOMING"};
    for (int index = 0; index < dates.length; index++) {
      var request = validBill();
      request.put("dueAt", dates[index]);
      mvc.perform(post("/api/v1/bills").contentType(MediaType.APPLICATION_JSON)
              .content(json.writeValueAsString(request)))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(initialStatuses[index]));
      assertThat(db.queryForObject("SELECT status FROM transactions WHERE record_kind='BILL' AND due_at=?",
          String.class, dates[index])).isEqualTo(initialStatuses[index]);
      mvc.perform(get("/api/v1/bills").param("status", initialStatuses[index]))
          .andExpect(jsonPath("$.length()").value(1));
    }
    configureClock("2026-09-25T18:30:01Z"); // September 26 in the bank's timezone.
    mvc.perform(get("/api/v1/bills?status=OVERDUE")).andExpect(jsonPath("$.length()").value(2));
    mvc.perform(get("/api/v1/bills?status=DUE")).andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].dueAt").value("2026-09-26"));
    mvc.perform(get("/api/v1/bills?status=UPCOMING")).andExpect(content().json("[]"));
    mvc.perform(get("/api/v1/bills?status=OVERDUE&size=1&page=1")).andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get("/api/v1/bills?status=OVERDUE&size=1&page=2")).andExpect(content().json("[]"));
    String overdueId = db.queryForObject("SELECT id FROM transactions WHERE record_kind='BILL' AND due_at='2026-09-25'", String.class);
    mvc.perform(get("/api/v1/bills/{id}", overdueId)).andExpect(jsonPath("$.status").value("OVERDUE"));
  }

  @Test
  void onlySuccessfulPostedBillPaymentsReduceOutstandingAndRecipientComesFromTheBoundAccount() throws Exception {
    var request = validBill();
    request.put("dueAt", "2026-09-25");
    create(request);
    String id = billId();
    paymentAccounts();
    db.update("UPDATE transactions SET target_id='saved-recipient',destination_account_id=9002 WHERE id=?", id);
    payment(id, "FAILED-ATTEMPT", "1000.50", "FAILED", null, "BILL_PAYMENT");
    payment(id, "UNPOSTED-SUCCESS", "1000.50", "SUCCESS", null, "BILL_PAYMENT");
    payment(id, "REVERSED-JOURNAL", "1000.50", "SUCCESS", "REVERSED", "BILL_PAYMENT");
    payment(id, "REVERSED-TRANSACTION", "1000.50", "REVERSED", "POSTED", "BILL_PAYMENT");
    payment(id, "UNRELATED-PAYMENT", "1000.50", "SUCCESS", "POSTED", "TRANSFER");
    mvc.perform(get("/api/v1/bills/{id}", id)).andExpect(jsonPath("$.status").value("OVERDUE"))
        .andExpect(jsonPath("$.paidAmount").value("0.00"))
        .andExpect(jsonPath("$.outstandingAmount").value("1000.50"))
        .andExpect(jsonPath("$.paymentHistory.length()").value(0));
    payment(id, "BILL-PARTIAL", "250.25", "SUCCESS", "POSTED", "BILL_PAYMENT");
    mvc.perform(get("/api/v1/bills/{id}", id)).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OVERDUE"))
        .andExpect(jsonPath("$.amount").value("1000.50"))
        .andExpect(jsonPath("$.paidAmount").value("250.25"))
        .andExpect(jsonPath("$.outstandingAmount").value("750.25"))
        .andExpect(jsonPath("$.payeeId").value("saved-recipient"))
        .andExpect(jsonPath("$.recipientName").value("Other owner"))
        .andExpect(jsonPath("$.recipientAccountMasked").value("•••• 9012"))
        .andExpect(jsonPath("$.paymentHistory.length()").value(1));
    payment(id, "BILL-REMAINDER", "750.25", "SUCCESS", "POSTED", "BILL_PAYMENT");
    mvc.perform(get("/api/v1/bills/{id}", id)).andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.amount").value("1000.50"))
        .andExpect(jsonPath("$.paidAmount").value("1000.50"))
        .andExpect(jsonPath("$.outstandingAmount").value("0.00"));
    mvc.perform(get("/api/v1/bills/{id}/payments", id)).andExpect(jsonPath("$.length()").value(2));
    mvc.perform(get("/api/v1/bills?status=PAID")).andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get("/api/v1/bills?status=OVERDUE")).andExpect(content().json("[]"));
  }

  @Test
  void historicalPaidBillsRemainNonPayableWhileFailedBillStatusesFollowTheDueDate() throws Exception {
    create(validBill());
    String id = billId();
    db.update("UPDATE transactions SET status='PAID' WHERE id=?", id);
    mvc.perform(get("/api/v1/bills/{id}", id)).andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.paidAmount").value("0.00"))
        .andExpect(jsonPath("$.outstandingAmount").value("0.00"));
    db.update("UPDATE transactions SET status='FAILED',due_at='2026-09-26' WHERE id=?", id);
    mvc.perform(get("/api/v1/bills/{id}", id)).andExpect(jsonPath("$.status").value("DUE"))
        .andExpect(jsonPath("$.outstandingAmount").value("1000.50"));
    mvc.perform(get("/api/v1/bills?status=FAILED")).andExpect(content().json("[]"));
    mvc.perform(get("/api/v1/bills?status=DUE")).andExpect(jsonPath("$.length()").value(1));
  }

  private void paymentAccounts() {
    db.update("INSERT INTO accounts(id,account_number,customer_id,account_name,account_type,account_category,"
        + "balance,status,created_at,updated_at) VALUES(9001,'987654321098',101,'Source','SAVINGS',"
        + "'CUSTOMER',0,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    db.update("INSERT INTO accounts(id,account_number,customer_id,account_name,account_type,account_category,"
        + "balance,status,created_at,updated_at) VALUES(9002,'123456789012',102,'Nickname is not verified identity','SAVINGS',"
        + "'CUSTOMER',0,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
  }

  private void savedPayee(String id, String payeeOwner, Long destination) throws Exception {
    String fingerprint = destination == null ? null : java.util.HexFormat.of().formatHex(
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(("NEXA:" + destination).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    db.update("INSERT INTO transactions(id,record_kind,user_id,status,display_name,bank_name,"
            + "destination_account_id,destination_hash,destination_masked,beneficiary_type,created_at,updated_at)"
            + " VALUES(?,'BENEFICIARY',?,'ACTIVE','Customer-entered nickname','Nexa',?,?,'•••• 9012','INTERNAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        id, payeeOwner, destination, fingerprint);
  }

  private void assertNoBillOrPosting() {
    assertThat(db.queryForObject("SELECT COUNT(*) FROM transactions WHERE record_kind IN ('BILL','PAYMENT')", Integer.class)).isZero();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM journal_entries", Integer.class)).isZero();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class)).isZero();
  }

  private void payment(String billId, String id, String amount, String status, String journalStatus, String operation) {
    db.update("INSERT INTO transactions(id,record_kind,transaction_type,source_account_id,destination_account_id,"
            + "target_id,operation,amount,status,currency_code,created_at)"
            + " VALUES(?,'PAYMENT','TRANSFER',9001,9002,?,?,?,?, 'INR',CURRENT_TIMESTAMP)",
        id, billId, operation, new BigDecimal(amount), status);
    if (journalStatus != null) {
      db.update("INSERT INTO journal_entries(transaction_id,entry_reference,entry_type,status,created_at)"
          + " VALUES(?,?,'TRANSACTION',?,CURRENT_TIMESTAMP)", id, "J-" + id, journalStatus);
      Long journalId = db.queryForObject("SELECT id FROM journal_entries WHERE transaction_id=?", Long.class, id);
      db.update("INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at)"
          + " VALUES(?,9001,'DEBIT',?,CURRENT_TIMESTAMP)", journalId, new BigDecimal(amount));
      db.update("INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at)"
          + " VALUES(?,9002,'CREDIT',?,CURRENT_TIMESTAMP)", journalId, new BigDecimal(amount));
    }
  }

  private void invalid(String field, Object value, String message) throws Exception {
    var bill = validBill();
    bill.put(field, value);
    mvc.perform(post("/api/v1/bills").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(bill)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.detail").value(startsWith(message)));
  }

  private void create(Map<String, Object> bill) throws Exception {
    mvc.perform(post("/api/v1/bills").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(bill))).andExpect(status().isOk());
  }

  private String billId() {
    return db.queryForObject("SELECT id FROM transactions WHERE record_kind='BILL'", String.class);
  }

  private Map<String, Object> validBill() {
    return new LinkedHashMap<>(Map.of("billerName", " City Electricity ", "amount", "1000.50",
        "minimumAmount", "250.25", "dueAt", "2026-10-05", "category", " Utilities ",
        "customerNumber", " 123456789 "));
  }
}
