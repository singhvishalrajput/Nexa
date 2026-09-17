package com.ofss.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.Filter;
import com.ofss.beans.*;
import com.ofss.exep.BadRequestException;
import com.ofss.exep.ResourceNotFoundException;
import com.ofss.repository.LoanPaymentDao;

/** Real service transactions on a disposable database; never uses configured Oracle credentials. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:nexaLoansTest;MODE=Oracle;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
    "spring.sql.init.mode=never", "spring.jpa.open-in-view=false",
    "app.jwt.secret=isolated-test-only-secret-at-least-32-characters",
    "app.loans.annual-interest-rate=14.50"
})
class LoanDatabaseTest {
    @Autowired LoanService loans;
    @Autowired TransactionService transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @Autowired JwtEncoder jwtEncoder;
    @MockitoBean OllamaService ollama;
    @MockitoSpyBean LoanPaymentDao paymentDao;

    @BeforeEach
    void seed() {
        for (String table : List.of("loan_payments", "loan_installments", "loans",
                "ledger_entries", "journal_entries", "transactions", "accounts", "customers"))
            jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO customers(id, full_name, email) VALUES (1, 'Loan Test', 'loan@example.test')");
        jdbc.update("INSERT INTO customers(id, full_name, email) VALUES (2, 'Other Test', 'other@example.test')");
        jdbc.update("INSERT INTO accounts(id, customer_id, account_number, account_name, account_type, account_category, status, balance)"
                + " VALUES (1, 1, 'LOAN-TEST-1', 'Loan Test', 'SAVINGS', 'CUSTOMER', 'ACTIVE', 100000)");
        jdbc.update("INSERT INTO accounts(id, customer_id, account_number, account_name, account_type, account_category, status, balance)"
                + " VALUES (2, 2, 'LOAN-TEST-2', 'Other Test', 'SAVINGS', 'CUSTOMER', 'ACTIVE', 100000)");
        jdbc.update("INSERT INTO accounts(id, account_number, account_name, account_type, account_category, status, balance)"
                + " VALUES (3, 'LOAN-TEST-CASH', 'System Cash', 'CASH', 'SYSTEM', 'ACTIVE', 1000000)");
    }

    @Test
    void approvedLoanDisbursesAndClosesWithoutTouchingLegacyTransactions() {
        LoanResponse approved = loans.apply(application("life-cycle"), 1L);
        assertEquals("APPROVED", approved.getStatus());
        assertMoney("0", approved.getOutstandingAmount());
        assertMoney("100000", balance(1));
        assertTrue(loans.schedule(approved.getId(), 1L).isEmpty());

        LoanResponse active = loans.accept(approved.getId(), 1L);
        assertEquals("ACTIVE", active.getStatus());
        assertMoney("112000", balance(1));
        LoanPaymentResponse disbursement = loans.payments(active.getId(), 1L).get(0);
        assertEquals(LoanPaymentType.DISBURSEMENT, disbursement.getType());
        assertNotNull(disbursement.getPaidAt());
        assertNull(disbursement.getInstallmentId());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name='LOANS' AND column_name='DISBURSED_AT'",
                Integer.class));
        List<LoanInstallmentResponse> schedule = loans.schedule(active.getId(), 1L);
        assertEquals(3, schedule.size());
        assertEquals(active.getNextDueDate(), schedule.get(0).getDueDate());
        BigDecimal repaid = BigDecimal.ZERO;
        for (LoanInstallmentResponse installment : schedule) {
            LoanPaymentResponse payment = loans.payInstallment(active.getId(), installment.getId(), 1L);
            assertEquals(LoanPaymentType.EMI_PAYMENT, payment.getType());
            repaid = repaid.add(installment.getTotalAmount());
        }
        LoanResponse closed = loans.getById(active.getId(), 1L);
        assertEquals("CLOSED", closed.getStatus());
        assertNotNull(closed.getClosedAt());
        assertNull(closed.getNextDueDate());
        assertMoney("0", closed.getOutstandingAmount());
        assertEquals(0, balance(1).compareTo(new BigDecimal("112000").subtract(repaid)));
        assertEquals(4, loans.payments(active.getId(), 1L).size());
        for (String table : List.of("transactions", "journal_entries", "ledger_entries"))
            assertEquals(0, count(table));
    }

    @Test
    void applicationAndAcceptanceRetriesDoNotDuplicateMoney() throws Exception {
        List<LoanResponse> responses = together(
                () -> loans.apply(application("repeat-apply"), 1L),
                () -> loans.apply(application("repeat-apply"), 1L));
        Long id = responses.get(0).getId();
        assertEquals(id, responses.get(1).getId());
        assertEquals(1, count("loans"));
        together(() -> loans.accept(id, 1L), () -> loans.accept(id, 1L));
        assertMoney("112000", balance(1));
        assertEquals(3, count("loan_installments"));
        assertEquals(1, count("loan_payments"));
        LoanApplicationRequest changed = application("repeat-apply");
        changed.setAmount(new BigDecimal("13000"));
        assertThrows(BadRequestException.class, () -> loans.apply(changed, 1L));
    }

    @Test
    void concurrentDuplicateEmiIsDebitedOnce() throws Exception {
        Long id = activeLoan("repeat-payment");
        LoanInstallmentResponse first = loans.schedule(id, 1L).get(0);
        List<LoanPaymentResponse> results = together(
                () -> loans.payInstallment(id, first.getId(), 1L),
                () -> loans.payInstallment(id, first.getId(), 1L));
        assertEquals(results.get(0).getId(), results.get(1).getId());
        assertEquals(0, balance(1).compareTo(new BigDecimal("112000").subtract(first.getTotalAmount())));
        assertEquals(2, count("loan_payments"));
    }

    @Test
    void replayAfterClosureReturnsOriginalPayment() {
        LoanApplicationRequest request = application("single-emi");
        request.setTenureMonths(1);
        Long id = loans.apply(request, 1L).getId();
        loans.accept(id, 1L);
        Long installmentId = loans.schedule(id, 1L).get(0).getId();
        LoanPaymentResponse paid = loans.payInstallment(id, installmentId, 1L);
        BigDecimal before = balance(1);
        assertEquals(paid.getId(), loans.payInstallment(id, installmentId, 1L).getId());
        assertEquals("CLOSED", loans.accept(id, 1L).getStatus());
        assertEquals(0, before.compareTo(balance(1)));
    }

    @Test
    void ownershipIsCheckedForEveryLoanOperation() {
        Long id = activeLoan("ownership");
        Long installmentId = loans.schedule(id, 1L).get(0).getId();
        assertThrows(ResourceNotFoundException.class, () -> loans.getById(id, 2L));
        assertThrows(ResourceNotFoundException.class, () -> loans.accept(id, 2L));
        assertThrows(ResourceNotFoundException.class, () -> loans.schedule(id, 2L));
        assertThrows(ResourceNotFoundException.class, () -> loans.payments(id, 2L));
        assertThrows(ResourceNotFoundException.class, () -> loans.payInstallment(id, installmentId, 2L));
        assertThrows(ResourceNotFoundException.class, () -> loans.apply(application("not-owner"), 2L));
        assertTrue(loans.byCustomer(2L).isEmpty());
        assertMoney("112000", balance(1));
    }

    @Test
    void invalidOrderAndInsufficientFundsLeaveLoanUnchanged() {
        Long id = activeLoan("funds");
        List<LoanInstallmentResponse> schedule = loans.schedule(id, 1L);
        assertThrows(BadRequestException.class, () -> loans.payInstallment(id, schedule.get(1).getId(), 1L));
        jdbc.update("UPDATE accounts SET balance=1 WHERE id=1");
        assertThrows(BadRequestException.class, () -> loans.payInstallment(id, schedule.get(0).getId(), 1L));
        assertMoney("1", balance(1));
        assertMoney("12000", loans.getById(id, 1L).getOutstandingAmount());
        assertEquals(1, count("loan_payments"));
        assertTrue(loans.schedule(id, 1L).stream().allMatch(row -> row.getPaidAt() == null));
    }

    @Test
    void blockedAccountCannotApplyDisburseOrRepay() {
        Long id = loans.apply(application("blocked"), 1L).getId();
        jdbc.update("UPDATE accounts SET status='BLOCKED' WHERE id=1");
        assertThrows(BadRequestException.class, () -> loans.apply(application("blocked-new"), 1L));
        assertThrows(BadRequestException.class, () -> loans.accept(id, 1L));
        jdbc.update("UPDATE accounts SET status='ACTIVE' WHERE id=1");
        loans.accept(id, 1L);
        Long installmentId = loans.schedule(id, 1L).get(0).getId();
        jdbc.update("UPDATE accounts SET status='BLOCKED' WHERE id=1");
        assertThrows(BadRequestException.class, () -> loans.payInstallment(id, installmentId, 1L));
        assertMoney("112000", balance(1));
    }

    @Test
    void disbursementFailureRollsBackBalanceScheduleAndStatus() {
        Long id = loans.apply(application("rollback"), 1L).getId();
        doThrow(new IllegalStateException("Simulated payment persistence failure")).when(paymentDao).save(any(LoanPayment.class));
        assertThrows(IllegalStateException.class, () -> loans.accept(id, 1L));
        assertMoney("100000", balance(1));
        assertEquals("APPROVED", loans.getById(id, 1L).getStatus());
        assertEquals(0, count("loan_installments"));
        assertEquals(0, count("loan_payments"));
    }

    @Test
    void repaymentFailureRollsBackDebitAndOutstanding() {
        Long id = activeLoan("repayment-rollback");
        Long installmentId = loans.schedule(id, 1L).get(0).getId();
        doThrow(new IllegalStateException("Simulated payment persistence failure")).when(paymentDao).save(any(LoanPayment.class));
        assertThrows(IllegalStateException.class, () -> loans.payInstallment(id, installmentId, 1L));
        assertMoney("112000", balance(1));
        assertMoney("12000", loans.getById(id, 1L).getOutstandingAmount());
        assertEquals(1, count("loan_payments"));
        assertNull(loans.schedule(id, 1L).get(0).getPaidAt());
    }

    @Test
    void concurrentTransferAndLoanDisbursementPreserveBothBalances() throws Exception {
        Long id = loans.apply(application("transfer-disburse"), 1L).getId();
        together(() -> loans.accept(id, 1L), () -> transactions.transfer(transfer("500")));
        assertMoney("111500", balance(1));
        assertMoney("100500", balance(2));
        assertEquals(1, count("transactions"));
        assertEquals(2, count("ledger_entries"));
    }

    @Test
    void concurrentTransferAndRepaymentPreserveBothDebits() throws Exception {
        Long id = activeLoan("transfer-repay");
        LoanInstallmentResponse first = loans.schedule(id, 1L).get(0);
        together(() -> loans.payInstallment(id, first.getId(), 1L), () -> transactions.transfer(transfer("500")));
        assertEquals(0, balance(1).compareTo(new BigDecimal("111500").subtract(first.getTotalAmount())));
        assertMoney("100500", balance(2));
    }

    @Test
    void existingDepositAndWithdrawalStillPostBalancedLedgerEntries() {
        TransactionRequest deposit = new TransactionRequest();
        deposit.setDestinationAccountId(1L);
        deposit.setAmount(new BigDecimal("1000"));
        transactions.deposit(deposit);
        TransactionRequest withdraw = new TransactionRequest();
        withdraw.setSourceAccountId(1L);
        withdraw.setAmount(new BigDecimal("250"));
        transactions.withdraw(withdraw);
        assertMoney("100750", balance(1));
        assertMoney("1000750", balance(3));
        assertEquals(2, count("transactions"));
        assertEquals(2, count("journal_entries"));
        assertEquals(4, count("ledger_entries"));
    }

    @Test
    void loanRoutesRequireAuthenticatedCustomerRole() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        mvc.perform(get("/api/loans")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/loans").header("Authorization", "Bearer " + token("ADMIN", "1")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/loans").header("Authorization", "Bearer " + token("CUSTOMER", "1")))
                .andExpect(status().isOk());
        Long id = activeLoan("http-owner");
        mvc.perform(get("/api/loans/" + id).header("Authorization", "Bearer " + token("CUSTOMER", "2")))
                .andExpect(status().isNotFound());
    }

    private String token(String role, String customerId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("banking-application").subject(customerId)
                .issuedAt(now).expiresAt(now.plusSeconds(120)).claim("role", role).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private LoanApplicationRequest application(String key) {
        LoanApplicationRequest request = new LoanApplicationRequest();
        request.setAccountId(1L);
        request.setApplicationKey(key);
        request.setPurpose("Personal expenses");
        request.setAmount(new BigDecimal("12000"));
        request.setTenureMonths(3);
        return request;
    }

    private Long activeLoan(String key) {
        Long id = loans.apply(application(key), 1L).getId();
        loans.accept(id, 1L);
        return id;
    }

    private TransactionRequest transfer(String amount) {
        TransactionRequest request = new TransactionRequest();
        request.setSourceAccountId(1L);
        request.setDestinationAccountId(2L);
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private BigDecimal balance(long id) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id=?", BigDecimal.class, id);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "Expected " + expected + " but got " + actual);
    }

    private <T> List<T> together(Supplier<T> first, Supplier<T> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<T> a = () -> { ready.countDown(); start.await(); return first.get(); };
            Callable<T> b = () -> { ready.countDown(); start.await(); return second.get(); };
            Future<T> one = executor.submit(a);
            Future<T> two = executor.submit(b);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
