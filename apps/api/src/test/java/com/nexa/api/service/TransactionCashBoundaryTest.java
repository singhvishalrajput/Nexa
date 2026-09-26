package com.nexa.api.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.nexa.api.beans.*;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.repository.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** Pure isolated tests: no application startup, real credentials, files or database connection. */
class TransactionCashBoundaryTest {
  private final AccountDao accounts = mock(AccountDao.class);
  private final TransactionDao transactions = mock(TransactionDao.class);
  private final JournalEntryDao journals = mock(JournalEntryDao.class);
  private final LedgerEntryDao ledgers = mock(LedgerEntryDao.class);
  private final EntityManager entities = mock(EntityManager.class);
  private TransactionServiceImpl service;
  private static final BigDecimal AMOUNT = new BigDecimal("1000.37");

  @BeforeEach
  void setup() {
    service = new TransactionServiceImpl();
    service.accountDao = accounts;
    service.transactionDao = transactions;
    service.journalEntryDao = journals;
    service.ledgerEntryDao = ledgers;
    service.entities = entities;
    when(transactions.save(any(BankTransaction.class))).thenAnswer(call -> {
      BankTransaction transaction = call.getArgument(0);
      ReflectionTestUtils.setField(transaction, "id", "TX-CASH-BOUNDARY-UNIT");
      return transaction;
    });
    when(journals.save(any(JournalEntry.class))).thenAnswer(call -> call.getArgument(0));
  }

  static Stream<Arguments> cashDirectionsAndLockOrders() {
    return Stream.of("SYSTEM-CASH", "OTHER-CASH").flatMap(number ->
        Stream.of(true, false).flatMap(cashFirstId ->
            Stream.of(true, false).map(cashSource -> Arguments.of(number, cashFirstId, cashSource))));
  }

  @ParameterizedTest
  @MethodSource("cashDirectionsAndLockOrders")
  void cashIsNeverATransferEndpointRegardlessOfNameDirectionOrIdOrder(
      String number, boolean cashFirstId, boolean cashSource) {
    Account cash = account(cashFirstId ? 1L : 9L, number, AccountType.CASH, "5000.00");
    Account customer = account(5L, "SAVINGS-UNIT", AccountType.SAVINGS, "5000.00");
    Account source = cashSource ? cash : customer, destination = cashSource ? customer : cash;

    assertThatThrownBy(() -> service.transfer(request(source, destination)))
        .isInstanceOf(InvalidRequestException.class).hasMessageContaining("CASH accounts cannot");

    assertUnchanged(cash, "5000.00"); assertUnchanged(customer, "5000.00");
    verify(accounts, never()).save(any());
    verifyNoInteractions(transactions, journals, ledgers);
    var order = inOrder(accounts, entities);
    Account first = cashFirstId ? cash : customer, second = cashFirstId ? customer : cash;
    order.verify(accounts).findLockedById(first.getId()); order.verify(entities).refresh(first);
    order.verify(accounts).findLockedById(second.getId()); order.verify(entities).refresh(second);
  }

  @Test
  void twoCashAccountsCannotTransferEither() {
    Account first = account(1L, "SYSTEM-CASH", AccountType.CASH, "5000.00");
    Account second = account(2L, "ANOTHER-CASH", AccountType.CASH, "5000.00");
    assertThatThrownBy(() -> service.transfer(request(first, second)))
        .isInstanceOf(InvalidRequestException.class).hasMessageContaining("CASH accounts cannot");
    assertUnchanged(first, "5000.00"); assertUnchanged(second, "5000.00");
    verify(accounts, never()).save(any()); verifyNoInteractions(transactions, journals, ledgers);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void ordinaryCustomerTransferKeepsLiabilitySignsAndBalancedEntries(boolean ascending) {
    Account source = account(ascending ? 1L : 9L, "FROM-UNIT", AccountType.SAVINGS, "5000.00");
    Account destination = account(5L, "TO-UNIT", AccountType.CURRENT, "2000.00");
    BankTransaction result = service.transfer(request(source, destination));
    assertThat(source.getBalance()).isEqualByComparingTo("3999.63");
    assertThat(destination.getBalance()).isEqualByComparingTo("3000.37");
    assertPosting(result, TransactionType.TRANSFER, source, destination);
    verify(accounts).save(source); verify(accounts).save(destination);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void depositStillLocksCashAndIncreasesDebitNormalCashAndCustomerBalance(boolean cashFirstId) {
    Account cash = cash(cashFirstId);
    Account customer = account(5L, "SAVINGS-UNIT", AccountType.SAVINGS, "2000.00");
    BankTransaction result = service.deposit(request(null, customer));
    assertThat(cash.getBalance()).isEqualByComparingTo("6000.37");
    assertThat(customer.getBalance()).isEqualByComparingTo("3000.37");
    assertPosting(result, TransactionType.DEPOSIT, cash, customer);
    assertCashLockOrder(cash, customer);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void withdrawalStillLocksCashAndReducesCreditPostedCashAndCustomerBalance(boolean cashFirstId) {
    Account cash = cash(cashFirstId);
    Account customer = account(5L, "SAVINGS-UNIT", AccountType.SAVINGS, "2000.00");
    BankTransaction result = service.withdraw(request(customer, null));
    assertThat(cash.getBalance()).isEqualByComparingTo("3999.63");
    assertThat(customer.getBalance()).isEqualByComparingTo("999.63");
    assertPosting(result, TransactionType.WITHDRAWAL, customer, cash);
    assertCashLockOrder(cash, customer);
  }

  private Account cash(boolean firstId) {
    Account cash = account(firstId ? 1L : 9L, "SYSTEM-CASH", AccountType.CASH, "5000.00");
    when(accounts.findByAccountCategoryAndAccountType(AccountCategory.SYSTEM, AccountType.CASH))
        .thenReturn(Optional.of(cash));
    return cash;
  }

  private void assertCashLockOrder(Account cash, Account customer) {
    var order = inOrder(accounts, entities);
    Account first = cash.getId() < customer.getId() ? cash : customer;
    Account second = first == cash ? customer : cash;
    order.verify(accounts).findLockedById(first.getId()); order.verify(entities).refresh(first);
    order.verify(accounts).findLockedById(second.getId()); order.verify(entities).refresh(second);
  }

  private Account account(long id, String number, AccountType type, String balance) {
    var account = new Account();
    account.setId(id); account.setAccountNumber(number); account.setAccountName(number);
    account.setAccountType(type);
    account.setAccountCategory(type == AccountType.CASH ? AccountCategory.SYSTEM : AccountCategory.CUSTOMER);
    account.setCurrencyCode("INR"); account.setStatus(AccountStatus.ACTIVE);
    account.setBalance(new BigDecimal(balance));
    ReflectionTestUtils.setField(account, "version", 7L);
    when(accounts.findLockedById(id)).thenReturn(Optional.of(account));
    return account;
  }

  private static TransactionRequest request(Account source, Account destination) {
    var request = new TransactionRequest(); request.setAmount(AMOUNT);
    request.setSourceAccountId(source == null ? null : source.getId());
    request.setDestinationAccountId(destination == null ? null : destination.getId());
    return request;
  }

  private static void assertUnchanged(Account account, String balance) {
    assertThat(account.getBalance()).isEqualByComparingTo(balance);
    assertThat(account.getVersion()).isEqualTo(7L);
    assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
  }

  private void assertPosting(BankTransaction result, TransactionType type, Account debit, Account credit) {
    assertThat(result.getTransactionType()).isEqualTo(type);
    assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
    assertThat(result.getAmount()).isEqualByComparingTo(AMOUNT);
    verify(transactions).save(result);
    var entries = ArgumentCaptor.forClass(LedgerEntry.class);
    verify(ledgers, times(2)).save(entries.capture());
    assertThat(entries.getAllValues()).extracting(LedgerEntry::getAccount).containsExactly(debit, credit);
    assertThat(entries.getAllValues()).extracting(LedgerEntry::getEntryType)
        .containsExactly(LedgerEntryType.DEBIT, LedgerEntryType.CREDIT);
    entries.getAllValues().forEach(entry -> assertThat(entry.getAmount()).isEqualByComparingTo(AMOUNT));
    assertThat(entries.getAllValues()).extracting(LedgerEntry::getJournalEntry).doesNotContainNull().containsOnly(entries.getValue().getJournalEntry());
    verify(journals).save(any(JournalEntry.class));
  }
}
