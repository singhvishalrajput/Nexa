package com.nexa.api.service;

import com.nexa.api.beans.Account;
import com.nexa.api.beans.AccountCategory;
import com.nexa.api.beans.AccountStatus;
import com.nexa.api.beans.AccountType;
import com.nexa.api.beans.BankTransaction;
import com.nexa.api.beans.JournalEntry;
import com.nexa.api.beans.JournalEntryStatus;
import com.nexa.api.beans.JournalEntryType;
import com.nexa.api.beans.LedgerEntry;
import com.nexa.api.beans.LedgerEntryType;
import com.nexa.api.beans.TransactionStatus;
import com.nexa.api.beans.TransactionType;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import com.nexa.api.repository.AccountDao;
import com.nexa.api.repository.JournalEntryDao;
import com.nexa.api.repository.LedgerEntryDao;
import com.nexa.api.repository.TransactionDao;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Internal posting boundary for manually acknowledged opening cash. The caller must lock and
 * validate the application/receipt, enforce idempotency, and write its audit records in this same
 * transaction. These methods do not establish that physical cash has actually changed hands.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class OpeningCashPostingService {
  public static final String HOLDING_NUMBER = "NEXA-OPENING-HOLD";
  private static final String CASH_NUMBER = "SYSTEM-CASH";
  private static final BigDecimal MINIMUM = new BigDecimal("1000.00");
  private static final BigDecimal MAXIMUM = new BigDecimal("10000000.00");
  private static final BigDecimal MAXIMUM_BALANCE = new BigDecimal("99999999999999999.99");

  private final AccountDao accounts;
  private final TransactionDao transactions;
  private final JournalEntryDao journals;
  private final LedgerEntryDao ledgers;
  private final JdbcTemplate db;
  private final CurrentUserProvider user;
  private final EntityManager entities;
  private final Clock clock;

  public OpeningCashPostingService(
      AccountDao accounts,
      TransactionDao transactions,
      JournalEntryDao journals,
      LedgerEntryDao ledgers,
      JdbcTemplate db,
      CurrentUserProvider user,
      EntityManager entities,
      Clock clock) {
    this.accounts = accounts;
    this.transactions = transactions;
    this.journals = journals;
    this.ledgers = ledgers;
    this.db = db;
    this.user = user;
    this.entities = entities;
    this.clock = clock;
  }

  public record InfrastructureReadiness(boolean cashAvailable, boolean holdingAvailable) {}

  /** Non-locking configuration check only, not proof of funds or permission to post. */
  @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
  public InfrastructureReadiness infrastructureReadiness() {
    return new InfrastructureReadiness(
        systemReady(CASH_NUMBER, AccountType.CASH), systemReady(HOLDING_NUMBER, AccountType.CLEARING));
  }

  private boolean systemReady(String number, AccountType type) {
    var account = accounts.findByAccountNumber(number);
    if (account.isEmpty()) return false;
    try {
      requireSystem(account.get(), number, type);
      checkedBalance(account.get());
      return true;
    } catch (InvalidRequestException invalid) { return false; }
  }

  public String receive(BigDecimal amount, String applicationId) {
    String actor = authorize();
    BigDecimal money = validate(amount, applicationId);
    var pair = locked(systemId(CASH_NUMBER), systemId(HOLDING_NUMBER));
    Account cash = pair[0], holding = pair[1];
    requireSystem(cash, CASH_NUMBER, AccountType.CASH);
    requireSystem(holding, HOLDING_NUMBER, AccountType.CLEARING);
    BigDecimal cashAfter = checkedBalance(cash).add(money);
    BigDecimal heldAfter = checkedBalance(holding).add(money);
    requireCapacity(cashAfter);
    requireCapacity(heldAfter);
    cash.setBalance(cashAfter);
    holding.setBalance(heldAfter);
    return post(TransactionType.DEPOSIT, null, holding, cash, holding, money,
        "OPENING_CASH_RECEIPT", applicationId, actor);
  }

  public String allocate(Long customerAccountId, BigDecimal amount, String applicationId) {
    String actor = authorize();
    BigDecimal money = validate(amount, applicationId);
    if (customerAccountId == null)
      throw new InvalidRequestException("An opening customer account is required.");
    var pair = locked(systemId(HOLDING_NUMBER), customerAccountId);
    Account holding = pair[0], customer = pair[1];
    requireSystem(holding, HOLDING_NUMBER, AccountType.CLEARING);
    if (customer.getAccountCategory() != AccountCategory.CUSTOMER
        || customer.getAccountType() != AccountType.SAVINGS
        || customer.getCustomer() == null
        || customer.getStatus() != AccountStatus.ACTIVE
        || !"INR".equals(customer.getCurrencyCode())
        || checkedBalance(customer).signum() != 0)
      throw new InvalidRequestException("Opening funds require an active, zero-balance INR savings account.");
    BigDecimal held = checkedBalance(holding);
    if (held.compareTo(money) < 0)
      throw new InvalidRequestException("The opening holding account has insufficient funds.");
    holding.setBalance(held.subtract(money));
    customer.setBalance(money);
    return post(TransactionType.TRANSFER, holding, customer, holding, customer, money,
        "OPENING_ALLOCATION", applicationId, actor);
  }

  public String refund(BigDecimal amount, String applicationId) {
    String actor = authorize();
    BigDecimal money = validate(amount, applicationId);
    var pair = locked(systemId(CASH_NUMBER), systemId(HOLDING_NUMBER));
    Account cash = pair[0], holding = pair[1];
    requireSystem(cash, CASH_NUMBER, AccountType.CASH);
    requireSystem(holding, HOLDING_NUMBER, AccountType.CLEARING);
    BigDecimal cashBefore = checkedBalance(cash), heldBefore = checkedBalance(holding);
    if (cashBefore.compareTo(money) < 0 || heldBefore.compareTo(money) < 0)
      throw new InvalidRequestException("Cash or opening holding funds are insufficient for this refund.");
    cash.setBalance(cashBefore.subtract(money));
    holding.setBalance(heldBefore.subtract(money));
    return post(TransactionType.WITHDRAWAL, holding, null, holding, cash, money,
        "OPENING_CASH_REFUND", applicationId, actor);
  }

  /** All ordinary account-management/posting paths must reject the workflow-owned account. */
  static void rejectReservedAccount(Account account) {
    if (account != null && account.getAccountNumber() != null
        && HOLDING_NUMBER.equalsIgnoreCase(account.getAccountNumber().strip()))
      throw new InvalidRequestException("The opening holding account is managed only by the account-opening workflow.");
  }

  static void rejectManagedAccountEdit(Account account) {
    rejectReservedAccount(account);
    if (account != null && (account.getAccountType() == AccountType.CASH
        || CASH_NUMBER.equalsIgnoreCase(account.getAccountNumber())))
      throw new InvalidRequestException("The system cash account cannot be changed through ordinary account management.");
  }

  private String authorize() {
    if (TransactionSynchronizationManager.isCurrentTransactionReadOnly())
      throw new IllegalStateException("Opening cash requires a writable workflow transaction.");
    String actor = user.userId();
    if (actor == null || actor.isBlank()) throw new AccessDeniedException("Administrator access required.");
    var roles = db.queryForList(
        "SELECT role FROM customers WHERE user_id=? AND status='ACTIVE' FOR UPDATE",
        String.class, actor);
    if (roles.size() != 1 || !"ADMIN".equals(roles.get(0)))
      throw new AccessDeniedException("Active administrator access required.");
    return actor;
  }

  private BigDecimal validate(BigDecimal amount, String applicationId) {
    if (amount == null || amount.compareTo(MINIMUM) < 0 || amount.compareTo(MAXIMUM) > 0
        || amount.scale() > 2)
      throw new InvalidRequestException("Opening cash must be INR 1000 to INR 10000000 with at most two decimals.");
    try {
      if (applicationId == null
          || !UUID.fromString(applicationId).toString().equals(applicationId))
        throw new IllegalArgumentException();
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException("A valid account application ID is required.");
    }
    return amount.setScale(2);
  }

  private Long systemId(String number) {
    return accounts.findByAccountNumber(number)
        .orElseThrow(() -> new ResourceNotFoundException("Required opening infrastructure account is missing."))
        .getId();
  }

  private Account[] locked(Long firstId, Long secondId) {
    if (firstId == null || secondId == null || firstId.equals(secondId))
      throw new InvalidRequestException("Opening postings require distinct persisted accounts.");
    Account low = lock(Math.min(firstId, secondId));
    Account high = lock(Math.max(firstId, secondId));
    return firstId.equals(low.getId()) ? new Account[] {low, high} : new Account[] {high, low};
  }

  private Account lock(Long id) {
    Account account = accounts.findLockedById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Opening posting account not found."));
    entities.refresh(account);
    return account;
  }

  private void requireSystem(Account account, String number, AccountType type) {
    if (!number.equals(account.getAccountNumber())
        || account.getAccountCategory() != AccountCategory.SYSTEM
        || account.getAccountType() != type || account.getCustomer() != null
        || !"INR".equals(account.getCurrencyCode())
        || account.getStatus() != AccountStatus.ACTIVE)
      throw new InvalidRequestException("Opening cash infrastructure is not active or has invalid configuration.");
  }

  private BigDecimal checkedBalance(Account account) {
    BigDecimal balance = account.getBalance();
    if (balance == null || balance.signum() < 0 || balance.scale() > 2)
      throw new InvalidRequestException("Opening posting account has an invalid balance.");
    requireCapacity(balance);
    return balance;
  }

  private void requireCapacity(BigDecimal balance) {
    if (balance.compareTo(MAXIMUM_BALANCE) > 0)
      throw new InvalidRequestException("The posting exceeds supported account balance capacity.");
  }

  private String post(TransactionType type, Account source, Account destination,
      Account debit, Account credit, BigDecimal amount, String operation,
      String applicationId, String actor) {
    var transaction = new BankTransaction();
    transaction.setTransactionType(type);
    transaction.setSourceAccount(source);
    transaction.setDestinationAccount(destination);
    transaction.setAmount(amount);
    transaction.setStatus(TransactionStatus.SUCCESS);
    transaction.setCompletedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    transaction.setOperation(operation);
    transaction.setCategory("ACCOUNT_OPENING");
    transaction.setPaymentMethod("CASH");
    transaction = transactions.save(transaction);
    var journal = new JournalEntry();
    journal.setTransaction(transaction);
    journal.setEntryReference("JE-" + transaction.getId());
    journal.setEntryType(JournalEntryType.TRANSACTION);
    journal.setStatus(JournalEntryStatus.POSTED);
    journal = journals.save(journal);
    entry(journal, debit, LedgerEntryType.DEBIT, amount);
    entry(journal, credit, LedgerEntryType.CREDIT, amount);
    entities.flush();
    if (db.update("UPDATE transactions SET target_id=?,user_id=?,currency_code='INR' WHERE id=?",
        applicationId, actor, transaction.getId()) != 1)
      throw new IllegalStateException("Opening posting metadata could not be recorded.");
    return transaction.getId();
  }

  private void entry(JournalEntry journal, Account account, LedgerEntryType type, BigDecimal amount) {
    var ledger = new LedgerEntry();
    ledger.setJournalEntry(journal);
    ledger.setAccount(account);
    ledger.setEntryType(type);
    ledger.setAmount(amount);
    ledgers.save(ledger);
  }
}
