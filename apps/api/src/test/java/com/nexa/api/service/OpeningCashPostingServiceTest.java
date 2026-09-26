package com.nexa.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nexa.api.beans.*;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.repository.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Isolated synthetic fixtures only; never loads application configuration or connects to Oracle. */
class OpeningCashPostingServiceTest {
  private static final String APPLICATION = "03f9a6e1-b9d1-4ef8-a3af-c2be729cb800";
  private static final String ADMIN = "test-admin";
  private static final String TX_ID = "TX-UNIT-OPENING";
  private static final String AUTH_SQL =
      "SELECT role FROM customers WHERE user_id=? AND status='ACTIVE' FOR UPDATE";
  private static final String META_SQL =
      "UPDATE transactions SET target_id=?,user_id=?,currency_code='INR' WHERE id=?";

  private final AccountDao accounts = mock(AccountDao.class);
  private final TransactionDao transactions = mock(TransactionDao.class);
  private final JournalEntryDao journals = mock(JournalEntryDao.class);
  private final LedgerEntryDao ledgers = mock(LedgerEntryDao.class);
  private final JdbcTemplate db = mock(JdbcTemplate.class);
  private final CurrentUserProvider user = mock(CurrentUserProvider.class);
  private final EntityManager entities = mock(EntityManager.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC);
  private OpeningCashPostingService service;
  private Account cash, holding, customer;

  @BeforeEach
  void setup() {
    service = new OpeningCashPostingService(accounts, transactions, journals, ledgers, db, user, entities, clock);
    when(user.userId()).thenReturn(ADMIN);
    when(db.queryForList(AUTH_SQL, String.class, ADMIN)).thenReturn(List.of("ADMIN"));
    cash = account(1L, "SYSTEM-CASH", AccountType.CASH, AccountCategory.SYSTEM, "0.00");
    holding = account(9L, OpeningCashPostingService.HOLDING_NUMBER,
        AccountType.CLEARING, AccountCategory.SYSTEM, "0.00");
    customer = account(5L, "TEST-SAVINGS", AccountType.SAVINGS, AccountCategory.CUSTOMER, "0.00");
    customer.setCustomer(new Customer());
    for (Account account : List.of(cash, holding, customer)) {
      when(accounts.findByAccountNumber(account.getAccountNumber())).thenReturn(Optional.of(account));
      when(accounts.findLockedById(account.getId())).thenReturn(Optional.of(account));
      when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
    }
    when(accounts.findByAccountCategoryAndAccountType(AccountCategory.SYSTEM, AccountType.CASH))
        .thenReturn(Optional.of(cash));
    when(transactions.save(any(BankTransaction.class))).thenAnswer(invocation -> {
      BankTransaction tx = invocation.getArgument(0);
      ReflectionTestUtils.setField(tx, "id", TX_ID);
      return tx;
    });
    when(journals.save(any(JournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(db.update(META_SQL, APPLICATION, ADMIN, TX_ID)).thenReturn(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1000", "2500.37", "10000000.00"})
  void receiptPostsActualAmountAsCashAndHoldingWithBalancedLedger(String value) {
    BigDecimal amount = new BigDecimal(value);
    assertThat(service.receive(amount, APPLICATION)).isEqualTo(TX_ID);
    assertThat(cash.getBalance()).isEqualByComparingTo(amount);
    assertThat(holding.getBalance()).isEqualByComparingTo(amount);
    assertPosting(TransactionType.DEPOSIT, null, holding, cash, holding, amount, "OPENING_CASH_RECEIPT");
    var order = inOrder(accounts, entities);
    order.verify(accounts).findLockedById(1L);
    order.verify(entities).refresh(cash);
    order.verify(accounts).findLockedById(9L);
    order.verify(entities).refresh(holding);
  }

  @Test
  void allocationConsumesHoldingWithoutIncreasingCashAndUsesAscendingLocks() {
    holding.setBalance(new BigDecimal("7500.00"));
    cash.setBalance(new BigDecimal("7500.00"));
    BigDecimal amount = new BigDecimal("2500.00");
    assertThat(service.allocate(customer.getId(), amount, APPLICATION)).isEqualTo(TX_ID);
    assertThat(holding.getBalance()).isEqualByComparingTo("5000.00");
    assertThat(customer.getBalance()).isEqualByComparingTo(amount);
    assertThat(cash.getBalance()).isEqualByComparingTo("7500.00");
    assertPosting(TransactionType.TRANSFER, holding, customer, holding, customer, amount, "OPENING_ALLOCATION");
    var order = inOrder(accounts, entities);
    order.verify(accounts).findLockedById(5L);
    order.verify(entities).refresh(customer);
    order.verify(accounts).findLockedById(9L);
    order.verify(entities).refresh(holding);
  }

  @Test
  void refundReducesCashAndHoldingWithoutCreatingCustomerMoney() {
    holding.setBalance(new BigDecimal("2500.00"));
    cash.setBalance(new BigDecimal("2500.00"));
    var amount = new BigDecimal("1500.50");
    service.refund(amount, APPLICATION);
    assertThat(cash.getBalance()).isEqualByComparingTo("999.50");
    assertThat(holding.getBalance()).isEqualByComparingTo("999.50");
    assertThat(customer.getBalance()).isZero();
    assertPosting(TransactionType.WITHDRAWAL, holding, null, holding, cash, amount, "OPENING_CASH_REFUND");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"0", "-1000", "999.99", "1000.001", "1000.000", "10000000.01", "1000000000000000000"})
  void invalidAmountsCannotPost(String value) {
    BigDecimal amount = value == null ? null : new BigDecimal(value);
    assertThatThrownBy(() -> service.receive(amount, APPLICATION)).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.allocate(customer.getId(), amount, APPLICATION)).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.refund(amount, APPLICATION)).isInstanceOf(InvalidRequestException.class);
    verifyNoInteractions(accounts, transactions, journals, ledgers);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "not-an-application", "03F9A6E1-B9D1-4EF8-A3AF-C2BE729CB800", "1-1-1-1-1"})
  void rejectsNonCanonicalApplicationIds(String id) {
    assertThatThrownBy(() -> service.receive(new BigDecimal("1000"), id)).isInstanceOf(InvalidRequestException.class);
    verifyNoInteractions(accounts, transactions, journals, ledgers);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTOMER", "SUPPORT", ""})
  void requiresActualActiveAdministrator(String role) {
    when(db.queryForList(AUTH_SQL, String.class, ADMIN)).thenReturn(role.isEmpty() ? List.of() : List.of(role));
    assertThatThrownBy(() -> service.receive(new BigDecimal("1000"), APPLICATION)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.allocate(customer.getId(), new BigDecimal("1000"), APPLICATION)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.refund(new BigDecimal("1000"), APPLICATION)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(accounts, transactions, journals, ledgers);
  }

  @Test
  void blockedHoldingIsNotActivatedAndCannotBeUsedByAnyPosting() {
    holding.setStatus(AccountStatus.BLOCKED);
    assertThatThrownBy(() -> service.receive(new BigDecimal("1000"), APPLICATION)).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.allocate(customer.getId(), new BigDecimal("1000"), APPLICATION)).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.refund(new BigDecimal("1000"), APPLICATION)).isInstanceOf(InvalidRequestException.class);
    assertThat(holding.getStatus()).isEqualTo(AccountStatus.BLOCKED);
    assertThat(cash.getBalance()).isZero();
    verifyNoInteractions(transactions, journals, ledgers);
  }

  @ParameterizedTest
  @ValueSource(strings = {"currency", "type", "category", "owner", "number", "cash-blocked"})
  void rejectsTamperedSystemAccountConfiguration(String change) {
    switch (change) {
      case "currency" -> holding.setCurrencyCode("USD");
      case "type" -> holding.setAccountType(AccountType.CASH);
      case "category" -> holding.setAccountCategory(AccountCategory.CUSTOMER);
      case "owner" -> holding.setCustomer(new Customer());
      case "number" -> holding.setAccountNumber("OTHER-HOLD");
      case "cash-blocked" -> cash.setStatus(AccountStatus.BLOCKED);
      default -> throw new AssertionError();
    }
    assertThatThrownBy(() -> service.receive(new BigDecimal("1000"), APPLICATION)).isInstanceOf(InvalidRequestException.class);
    assertThat(cash.getBalance()).isZero();
    verifyNoInteractions(transactions, journals, ledgers);
  }

  @Test
  void checksBalanceCapacityBeforeChangingEitherBalance() {
    cash.setBalance(new BigDecimal("99999999999999999.99"));
    assertThatThrownBy(() -> service.receive(new BigDecimal("1000"), APPLICATION)).isInstanceOf(InvalidRequestException.class);
    assertThat(cash.getBalance()).isEqualByComparingTo("99999999999999999.99");
    assertThat(holding.getBalance()).isZero();
    verifyNoInteractions(transactions, journals, ledgers);
  }

  @Test
  void insufficientHeldMoneyCannotBeAllocatedAndNoBalanceChanges() {
    holding.setBalance(new BigDecimal("999.99"));
    assertThatThrownBy(() -> service.allocate(customer.getId(), new BigDecimal("1000"), APPLICATION))
        .isInstanceOf(InvalidRequestException.class);
    assertThat(customer.getBalance()).isZero();
    assertThat(holding.getBalance()).isEqualByComparingTo("999.99");
    verifyNoInteractions(transactions, journals, ledgers);
  }

  @ParameterizedTest
  @ValueSource(strings = {"cash", "holding"})
  void refundRequiresBothCashAndUnallocatedHoldingMoney(String insufficient) {
    cash.setBalance(new BigDecimal("2000"));
    holding.setBalance(new BigDecimal("2000"));
    (insufficient.equals("cash") ? cash : holding).setBalance(new BigDecimal("999.99"));
    BigDecimal oldCash = cash.getBalance(), oldHolding = holding.getBalance();
    assertThatThrownBy(() -> service.refund(new BigDecimal("1000"), APPLICATION)).isInstanceOf(InvalidRequestException.class);
    assertThat(cash.getBalance()).isEqualTo(oldCash);
    assertThat(holding.getBalance()).isEqualTo(oldHolding);
    verifyNoInteractions(transactions, journals, ledgers);
  }

  @ParameterizedTest
  @ValueSource(strings = {"nonzero", "currency", "current", "blocked", "system", "no-owner"})
  void allocationOnlyTargetsUnfundedPersonalSavings(String change) {
    holding.setBalance(new BigDecimal("1000"));
    switch (change) {
      case "nonzero" -> customer.setBalance(BigDecimal.ONE);
      case "currency" -> customer.setCurrencyCode("USD");
      case "current" -> customer.setAccountType(AccountType.CURRENT);
      case "blocked" -> customer.setStatus(AccountStatus.BLOCKED);
      case "system" -> customer.setAccountCategory(AccountCategory.SYSTEM);
      case "no-owner" -> customer.setCustomer(null);
      default -> throw new AssertionError();
    }
    assertThatThrownBy(() -> service.allocate(customer.getId(), new BigDecimal("1000"), APPLICATION))
        .isInstanceOf(InvalidRequestException.class);
    assertThat(holding.getBalance()).isEqualByComparingTo("1000");
    verifyNoInteractions(transactions, journals, ledgers);
  }

  @Test
  void mandatorySpringTransactionRejectsCallsWithoutAnExistingTransaction() {
    var proxy = new ProxyFactory(service);
    proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(mock(DataSource.class)),
        new AnnotationTransactionAttributeSource()));
    var guarded = (OpeningCashPostingService) proxy.getProxy();
    assertThatThrownBy(() -> guarded.receive(new BigDecimal("1000"), APPLICATION))
        .isInstanceOf(IllegalTransactionStateException.class);
    verifyNoInteractions(accounts, transactions, journals, ledgers);
  }

  @Test
  void cannotJoinAReadOnlyTransactionAndLoseBalanceUpdates() {
    TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
    try {
      assertThatThrownBy(() -> service.receive(new BigDecimal("1000"), APPLICATION))
          .isInstanceOf(IllegalStateException.class).hasMessageContaining("writable");
      verifyNoInteractions(accounts, transactions, journals, ledgers);
    } finally {
      TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"deposit", "withdraw", "transfer-from", "transfer-to"})
  void genericMoneyOperationsCannotAccessReservedHoldingEvenWhenActive(String operation) {
    var generic = new TransactionServiceImpl();
    generic.accountDao = accounts;
    generic.transactionDao = transactions;
    generic.journalEntryDao = journals;
    generic.ledgerEntryDao = ledgers;
    generic.entities = entities;
    var request = new TransactionRequest();
    request.setAmount(new BigDecimal("1000"));
    request.setSourceAccountId(operation.equals("transfer-to") ? customer.getId() : holding.getId());
    request.setDestinationAccountId(operation.equals("transfer-from") ? customer.getId() : holding.getId());
    assertThatThrownBy(() -> {
      switch (operation) {
        case "deposit" -> generic.deposit(request);
        case "withdraw" -> generic.withdraw(request);
        default -> generic.transfer(request);
      }
    }).isInstanceOf(InvalidRequestException.class).hasMessageContaining("holding account");
    verifyNoInteractions(transactions, journals, ledgers);
  }

  @Test
  void legacyCreateCannotOverwriteExistingAccountOrCreateReservedNumber() {
    var generic = new AccountServiceImpl();
    generic.accounts = accounts;
    assertThatThrownBy(() -> generic.create(holding)).isInstanceOf(InvalidRequestException.class);
    holding.setId(null);
    assertThatThrownBy(() -> generic.create(holding)).isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("holding account");
    verify(accounts, never()).save(any());
  }

  @Test
  void legacyAndModernAccountEditCannotRenameOrActivateReservedHolding() {
    var legacy = new AccountServiceImpl();
    legacy.accounts = accounts;
    var input = new Account();
    input.setAccountName("Changed");
    input.setStatus(AccountStatus.ACTIVE);
    assertThatThrownBy(() -> legacy.update(holding.getId(), input)).isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("holding account");
    var modern = new AdminAccountService(db, accounts, user, mock(TransactionService.class), entities);
    assertThatThrownBy(() -> modern.edit(holding.getId(),
        new AdminAccountService.Edit("Changed", AccountStatus.ACTIVE, 0L, "test reason")))
        .isInstanceOf(InvalidRequestException.class).hasMessageContaining("holding account");
    assertThat(holding.getAccountName()).isEqualTo(OpeningCashPostingService.HOLDING_NUMBER);
    verify(accounts, never()).save(any());
  }

  @Test
  void modernAdjustmentCannotCreditOrDebitReservedHolding() {
    when(db.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
    var generic = mock(TransactionService.class);
    var modern = new AdminAccountService(db, accounts, user, generic, entities);
    assertThatThrownBy(() -> modern.adjust(holding.getId(), new AdminAccountService.Adjustment(
        "CREDIT", new BigDecimal("1000"), "test reason", APPLICATION)))
        .isInstanceOf(InvalidRequestException.class).hasMessageContaining("holding account");
    verifyNoInteractions(generic);
  }

  @Test
  void readinessReadsConfigurationWithoutTakingPostingLocksOrWritingBalances() {
    var result = service.infrastructureReadiness();
    assertThat(result.cashAvailable()).isTrue();
    assertThat(result.holdingAvailable()).isTrue();
    assertThat(cash.getBalance()).isZero(); assertThat(holding.getBalance()).isZero();
    verify(accounts, never()).findLockedById(any());
    verify(accounts, never()).save(any());
    verifyNoInteractions(transactions, journals, ledgers, entities, db, user);
  }

  @ParameterizedTest
  @ValueSource(strings = {"cash-missing", "holding-missing", "cash-type", "holding-type",
      "cash-category", "holding-category", "cash-owner", "holding-owner", "cash-number", "holding-number",
      "cash-currency", "holding-currency", "cash-blocked", "holding-blocked", "cash-null", "holding-null",
      "cash-negative", "holding-negative", "cash-precision", "holding-precision", "cash-overflow", "holding-overflow"})
  void readinessUsesTheSameSystemAndBalanceChecksAsActualCashPostings(String problem) {
    String[] parts = problem.split("-");
    Account target = parts[0].equals("cash") ? cash : holding;
    switch (parts[1]) {
      case "missing" -> when(accounts.findByAccountNumber(target.getAccountNumber())).thenReturn(Optional.empty());
      case "type" -> target.setAccountType(AccountType.SAVINGS);
      case "category" -> target.setAccountCategory(AccountCategory.CUSTOMER);
      case "owner" -> target.setCustomer(new Customer());
      case "number" -> target.setAccountNumber("WRONG-INTERNAL-NUMBER");
      case "currency" -> target.setCurrencyCode("USD");
      case "blocked" -> target.setStatus(AccountStatus.BLOCKED);
      case "null" -> target.setBalance(null);
      case "negative" -> target.setBalance(new BigDecimal("-0.01"));
      case "precision" -> target.setBalance(new BigDecimal("0.001"));
      case "overflow" -> target.setBalance(new BigDecimal("100000000000000000.00"));
      default -> throw new AssertionError(problem);
    }
    var result = service.infrastructureReadiness();
    assertThat(result.cashAvailable()).isEqualTo(target != cash);
    assertThat(result.holdingAvailable()).isEqualTo(target != holding);
    verify(accounts, never()).findLockedById(any()); verify(accounts, never()).save(any());
    verifyNoInteractions(transactions, journals, ledgers, entities, db, user);
  }

  private void assertPosting(TransactionType type, Account source, Account destination,
      Account debit, Account credit, BigDecimal amount, String operation) {
    var tx = ArgumentCaptor.forClass(BankTransaction.class);
    verify(transactions).save(tx.capture());
    assertThat(tx.getValue().getTransactionType()).isEqualTo(type);
    assertThat(tx.getValue().getSourceAccount()).isSameAs(source);
    assertThat(tx.getValue().getDestinationAccount()).isSameAs(destination);
    assertThat(tx.getValue().getAmount()).isEqualByComparingTo(amount);
    assertThat(tx.getValue().getStatus()).isEqualTo(TransactionStatus.SUCCESS);
    assertThat(tx.getValue().getPaymentMethod()).isEqualTo("CASH");
    assertThat(ReflectionTestUtils.getField(tx.getValue(), "operation")).isEqualTo(operation);
    assertThat(tx.getValue().getCompletedAt().toInstant(ZoneOffset.UTC)).isEqualTo(clock.instant());
    var journal = ArgumentCaptor.forClass(JournalEntry.class);
    verify(journals).save(journal.capture());
    assertThat(journal.getValue().getStatus()).isEqualTo(JournalEntryStatus.POSTED);
    assertThat(journal.getValue().getTransaction()).isSameAs(tx.getValue());
    var entries = ArgumentCaptor.forClass(LedgerEntry.class);
    verify(ledgers, org.mockito.Mockito.times(2)).save(entries.capture());
    assertThat(entries.getAllValues()).extracting(LedgerEntry::getAccount).containsExactly(debit, credit);
    assertThat(entries.getAllValues()).extracting(LedgerEntry::getEntryType)
        .containsExactly(LedgerEntryType.DEBIT, LedgerEntryType.CREDIT);
    entries.getAllValues().forEach(entry -> assertThat(entry.getAmount()).isEqualByComparingTo(amount));
    verify(entities).flush();
    verify(db).update(META_SQL, APPLICATION, ADMIN, TX_ID);
  }

  private static Account account(Long id, String number, AccountType type,
      AccountCategory category, String balance) {
    var account = new Account();
    account.setId(id);
    account.setAccountNumber(number);
    account.setAccountName(number);
    account.setAccountType(type);
    account.setAccountCategory(category);
    account.setStatus(AccountStatus.ACTIVE);
    account.setBalance(new BigDecimal(balance));
    account.setCurrencyCode("INR");
    return account;
  }
}
