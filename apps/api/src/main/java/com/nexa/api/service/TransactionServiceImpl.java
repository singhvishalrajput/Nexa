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
import com.nexa.api.beans.TransactionRequest;
import com.nexa.api.beans.TransactionStatus;
import com.nexa.api.beans.TransactionType;
import com.nexa.api.exep.InvalidRequestException;
import com.nexa.api.exep.ResourceNotFoundException;
import com.nexa.api.repository.AccountDao;
import com.nexa.api.repository.JournalEntryDao;
import com.nexa.api.repository.LedgerEntryDao;
import com.nexa.api.repository.TransactionDao;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionServiceImpl implements TransactionService {

  @Autowired AccountDao accountDao;

  @Autowired TransactionDao transactionDao;

  @Autowired JournalEntryDao journalEntryDao;

  @Autowired LedgerEntryDao ledgerEntryDao;
  @Autowired jakarta.persistence.EntityManager entities;

  @Override
  public List<BankTransaction> getAll() {
    return transactionDao.findAll();
  }

  @Override
  public BankTransaction getById(String id) {
    return transactionDao
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
  }

  @Override
  public List<BankTransaction> byAccount(Long accountId) {
    return transactionDao.findBySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(
        accountId, accountId);
  }

  @Override
  @Transactional
  public BankTransaction deposit(TransactionRequest request) {
    validateAmount(request.getAmount());

    Account cashAccount = lockCashAndCustomer(request.getDestinationAccountId());
    Account destinationAccount = getActiveAccount(request.getDestinationAccountId());
    if (!cashAccount.getCurrencyCode().equals(destinationAccount.getCurrencyCode()))
      throw new InvalidRequestException("Cash and customer currencies must match");

    BankTransaction transaction = new BankTransaction();
    transaction.setTransactionType(TransactionType.DEPOSIT);
    transaction.setDestinationAccount(destinationAccount);
    transaction.setAmount(request.getAmount());
    transaction.setStatus(TransactionStatus.SUCCESS);
    transaction.setCompletedAt(LocalDateTime.now());
    transaction = transactionDao.save(transaction);

    destinationAccount.setBalance(destinationAccount.getBalance().add(request.getAmount()));
    cashAccount.setBalance(cashAccount.getBalance().add(request.getAmount()));
    accountDao.save(destinationAccount);
    accountDao.save(cashAccount);

    JournalEntry journalEntry = createJournalEntry(transaction);
    createLedgerEntry(journalEntry, cashAccount, LedgerEntryType.DEBIT, request.getAmount());
    createLedgerEntry(
        journalEntry, destinationAccount, LedgerEntryType.CREDIT, request.getAmount());

    return transaction;
  }

  @Override
  @Transactional
  public BankTransaction withdraw(TransactionRequest request) {
    validateAmount(request.getAmount());

    Account cashAccount = lockCashAndCustomer(request.getSourceAccountId());
    Account sourceAccount = getActiveAccount(request.getSourceAccountId());
    if (!cashAccount.getCurrencyCode().equals(sourceAccount.getCurrencyCode()))
      throw new InvalidRequestException("Cash and customer currencies must match");

    if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
      throw new InvalidRequestException("Insufficient customer balance");
    }

    if (cashAccount.getBalance().compareTo(request.getAmount()) < 0) {
      throw new InvalidRequestException("Insufficient cash in system account");
    }

    BankTransaction transaction = new BankTransaction();
    transaction.setTransactionType(TransactionType.WITHDRAWAL);
    transaction.setSourceAccount(sourceAccount);
    transaction.setAmount(request.getAmount());
    transaction.setStatus(TransactionStatus.SUCCESS);
    transaction.setCompletedAt(LocalDateTime.now());
    transaction = transactionDao.save(transaction);

    sourceAccount.setBalance(sourceAccount.getBalance().subtract(request.getAmount()));
    cashAccount.setBalance(cashAccount.getBalance().subtract(request.getAmount()));
    accountDao.save(sourceAccount);
    accountDao.save(cashAccount);

    JournalEntry journalEntry = createJournalEntry(transaction);
    createLedgerEntry(journalEntry, sourceAccount, LedgerEntryType.DEBIT, request.getAmount());
    createLedgerEntry(journalEntry, cashAccount, LedgerEntryType.CREDIT, request.getAmount());

    return transaction;
  }

  @Override
  @Transactional
  public BankTransaction transfer(TransactionRequest request) {
    validateAmount(request.getAmount());

    if (request.getSourceAccountId() == null || request.getDestinationAccountId() == null)
      throw new InvalidRequestException("Both accounts are required");
    // Stable lock order serializes postings even across different conversations.
    getActiveAccount(Math.min(request.getSourceAccountId(), request.getDestinationAccountId()));
    getActiveAccount(Math.max(request.getSourceAccountId(), request.getDestinationAccountId()));

    Account sourceAccount = getActiveAccount(request.getSourceAccountId());
    Account destinationAccount = getActiveAccount(request.getDestinationAccountId());

    if (!sourceAccount.getCurrencyCode().equals(destinationAccount.getCurrencyCode()))
      throw new InvalidRequestException("Account currencies must match");

    if (sourceAccount.getId().equals(destinationAccount.getId())) {
      throw new InvalidRequestException("Source and destination accounts must be different");
    }

    if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
      throw new InvalidRequestException("Insufficient balance");
    }

    BankTransaction transaction = new BankTransaction();
    transaction.setTransactionType(TransactionType.TRANSFER);
    transaction.setSourceAccount(sourceAccount);
    transaction.setDestinationAccount(destinationAccount);
    transaction.setAmount(request.getAmount());
    transaction.setStatus(TransactionStatus.SUCCESS);
    transaction.setCompletedAt(LocalDateTime.now());
    transaction = transactionDao.save(transaction);

    sourceAccount.setBalance(sourceAccount.getBalance().subtract(request.getAmount()));
    destinationAccount.setBalance(destinationAccount.getBalance().add(request.getAmount()));
    accountDao.save(sourceAccount);
    accountDao.save(destinationAccount);

    JournalEntry journalEntry = createJournalEntry(transaction);
    createLedgerEntry(journalEntry, sourceAccount, LedgerEntryType.DEBIT, request.getAmount());
    createLedgerEntry(
        journalEntry, destinationAccount, LedgerEntryType.CREDIT, request.getAmount());

    return transaction;
  }

  private Account getActiveAccount(Long accountId) {
    if (accountId == null) {
      throw new InvalidRequestException("Account id is required");
    }

    Account account =
        accountDao
            .findLockedById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
    entities.refresh(account);

    if (account.getAccountType() == AccountType.LOAN
        || account.getAccountType() == AccountType.CARD)
      throw new InvalidRequestException("Use the product payment endpoint for this account");
    if (account.getStatus() != AccountStatus.ACTIVE) {
      throw new InvalidRequestException("Account must be ACTIVE");
    }

    return account;
  }

  private Account getCashAccount() {
    Account cashAccount =
        accountDao
            .findByAccountCategoryAndAccountType(AccountCategory.SYSTEM, AccountType.CASH)
            .orElseThrow(() -> new ResourceNotFoundException("System cash account not found"));

    if (cashAccount.getStatus() != AccountStatus.ACTIVE) {
      throw new InvalidRequestException("System cash account must be ACTIVE");
    }

    return cashAccount;
  }

  private Account lockCashAndCustomer(Long customerId) {
    if (customerId == null) throw new InvalidRequestException("Account id is required");
    Account cash = getCashAccount();
    if (cash.getId().equals(customerId))
      throw new InvalidRequestException("Choose a customer deposit account");
    getActiveAccount(Math.min(cash.getId(), customerId));
    getActiveAccount(Math.max(cash.getId(), customerId));
    Account customer = getActiveAccount(customerId);
    if (customer.getAccountCategory() != AccountCategory.CUSTOMER)
      throw new InvalidRequestException("Choose a customer deposit account");
    return cash;
  }

  private void validateAmount(BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidRequestException("Amount must be greater than zero");
    }
    if (amount.scale() > 2 || amount.precision() - amount.scale() > 13)
      throw new InvalidRequestException(
          "Amount must have at most two decimals and thirteen integer digits");
  }

  private JournalEntry createJournalEntry(BankTransaction transaction) {
    JournalEntry journalEntry = new JournalEntry();
    journalEntry.setTransaction(transaction);
    journalEntry.setEntryReference("JE-" + transaction.getId());
    journalEntry.setEntryType(JournalEntryType.TRANSACTION);
    journalEntry.setStatus(JournalEntryStatus.POSTED);

    return journalEntryDao.save(journalEntry);
  }

  private void createLedgerEntry(
      JournalEntry journalEntry, Account account, LedgerEntryType entryType, BigDecimal amount) {

    LedgerEntry ledgerEntry = new LedgerEntry();
    ledgerEntry.setJournalEntry(journalEntry);
    ledgerEntry.setAccount(account);
    ledgerEntry.setEntryType(entryType);
    ledgerEntry.setAmount(amount);

    ledgerEntryDao.save(ledgerEntry);
  }
}
