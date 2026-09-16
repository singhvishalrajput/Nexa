package com.nexa.api.core;
import com.nexa.api.beans.Account;
import com.nexa.api.beans.AccountCategory;
import com.nexa.api.beans.AccountStatus;
import com.nexa.api.beans.AccountType;
import com.nexa.api.beans.Customer;
import com.nexa.api.beans.LedgerEntry;
import com.nexa.api.beans.LedgerEntryType;
import com.nexa.api.beans.TransactionRequest;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import com.nexa.api.repository.AccountDao;
import com.nexa.api.repository.JournalEntryDao;
import com.nexa.api.repository.LedgerEntryDao;
import com.nexa.api.repository.TransactionDao;
import com.nexa.api.service.AccountQueryService;
import com.nexa.api.service.AccountService;
import com.nexa.api.service.AccountServiceImpl;
import com.nexa.api.service.CurrentUserProvider;
import com.nexa.api.service.CustomerService;
import com.nexa.api.service.CustomerServiceImpl;
import com.nexa.api.service.TransactionQueryService;
import com.nexa.api.service.TransactionService;
import com.nexa.api.service.TransactionServiceImpl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.nexa.api.service.AccountQueryService;
import com.nexa.api.service.CurrentUserProvider;
import com.nexa.api.service.TransactionQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest(
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.profiles.active=integration"
    })
@Import({
  TransactionServiceImpl.class,
  AccountServiceImpl.class,
  CustomerServiceImpl.class,
  AccountQueryService.class,
  TransactionQueryService.class
})
class BankingCoreIntegrationTest {
  @Autowired TransactionService transactions;
  @Autowired AccountService accounts;
  @Autowired CustomerService customers;
  @Autowired AccountDao accountDao;
  @Autowired TransactionDao transactionDao;
  @Autowired LedgerEntryDao ledgers;
  @Autowired JournalEntryDao journals;
  @Autowired AccountQueryService accountQueries;
  @Autowired TransactionQueryService queries;
  @MockitoBean CurrentUserProvider user;
  Account a, b, cash;

  @BeforeEach
  void setup() {
    when(user.userId()).thenReturn("owner");
    Customer c = new Customer();
    c.setFullName("Owner");
    c.setEmail("owner@example.com");
    c.setDateOfBirth(LocalDate.of(1990, 1, 1));
    c.setAddress("Mumbai");
    c.setUserId("owner");
    c = customers.create(c);
    a = account("A", AccountType.SAVINGS, c);
    b = account("B", AccountType.CURRENT, c);
    cash = account("CASH", AccountType.CASH, null);
  }

  Account account(String n, AccountType type, Customer c) {
    var a = new Account();
    a.setAccountNumber(n);
    a.setAccountName(n);
    a.setAccountType(type);
    a.setCustomer(c);
    a.setAccountCategory(c == null ? AccountCategory.SYSTEM : AccountCategory.CUSTOMER);
    return accounts.create(a);
  }

  TransactionRequest request(Account from, Account to, String amount) {
    var r = new TransactionRequest();
    if (from != null) r.setSourceAccountId(from.getId());
    if (to != null) r.setDestinationAccountId(to.getId());
    r.setAmount(new BigDecimal(amount));
    return r;
  }

  @Test
  void depositWithdrawalAndTransferShareBalancesHistoryAndBalancedJournals() {
    var deposit = transactions.deposit(request(null, a, "100.00"));
    transactions.transfer(request(a, b, "30.00"));
    transactions.withdraw(request(a, null, "20.00"));
    accountDao.flush();
    assertThat(a.getBalance()).isEqualByComparingTo("50.00");
    assertThat(b.getBalance()).isEqualByComparingTo("30.00");
    assertThat(cash.getBalance()).isEqualByComparingTo("80.00");
    assertThat(deposit.getId()).startsWith("TX-");
    assertThat(journals.count()).isEqualTo(3);
    assertThat(ledgers.count()).isEqualTo(6);
    for (var journal : journals.findAll()) {
      var entries = ledgers.findByJournalEntryId(journal.getId());
      assertThat(entries).hasSize(2);
      assertThat(entries.get(0).getAmount()).isEqualByComparingTo(entries.get(1).getAmount());
      assertThat(entries)
          .extracting(LedgerEntry::getEntryType)
          .containsExactlyInAnyOrder(LedgerEntryType.DEBIT, LedgerEntryType.CREDIT);
    }
    assertThat(accountQueries.balance(a.getId().toString()).availableBalance())
        .isEqualByComparingTo("50.00");
    assertThat(queries.transactions(a.getId().toString(), null, null, null, 0, 10).totalElements())
        .isEqualTo(3);
    assertThat(
            queries
                .transactions(a.getId().toString(), null, null, null, 0, 10, "DEBIT", null)
                .totalElements())
        .isEqualTo(2);
    assertThat(
            queries
                .transactions(b.getId().toString(), null, null, null, 0, 10)
                .content()
                .get(0)
                .amount())
        .isEqualByComparingTo("30.00");
    assertThat(queries.detail(deposit.getId()).amount()).isEqualByComparingTo("100.00");
  }

  @Test
  void rejectedTransactionsDoNotChangeBalancesOrCreatePostings() {
    assertThatThrownBy(() -> transactions.withdraw(request(a, null, "1")))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> transactions.deposit(request(null, a, "0")))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> transactions.transfer(request(a, a, "1")))
        .isInstanceOf(InvalidRequestException.class);
    assertThat(transactionDao.count()).isZero();
    assertThat(journals.count()).isZero();
    assertThat(ledgers.count()).isZero();
    assertThat(a.getBalance()).isZero();
  }

  @Test
  void blockedAccountsCannotReceiveDeposits() {
    a.setStatus(AccountStatus.BLOCKED);
    assertThatThrownBy(() -> transactions.deposit(request(null, a, "10")))
        .isInstanceOf(InvalidRequestException.class);
    assertThat(transactionDao.count()).isZero();
  }

  @Test
  void accountAndTransactionOwnershipIsEnforced() {
    var deposit = transactions.deposit(request(null, a, "10"));
    when(user.userId()).thenReturn("other");
    assertThat(accountQueries.currentAccounts()).isEmpty();
    assertThatThrownBy(() -> accountQueries.requireOwnedAccount(a.getId().toString()))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> queries.detail(deposit.getId()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void customerValidationAndSystemAccountRulesRemainAuthoritative() {
    assertThatThrownBy(() -> customers.create(new Customer()))
        .isInstanceOf(InvalidRequestException.class);
    var invalid = new Account();
    invalid.setAccountNumber("INVALID");
    invalid.setAccountCategory(AccountCategory.SYSTEM);
    invalid.setCustomer(a.getCustomer());
    assertThatThrownBy(() -> accounts.create(invalid)).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
            () -> accounts.getAccountsByStatusCreatedWithinDays(AccountStatus.ACTIVE, -1))
        .isInstanceOf(InvalidRequestException.class);
  }
}
