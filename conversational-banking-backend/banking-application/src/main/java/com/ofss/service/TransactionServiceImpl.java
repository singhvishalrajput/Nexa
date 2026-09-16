package com.ofss.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.*;
import com.ofss.exep.BadRequestException;
import com.ofss.exep.ResourceNotFoundException;
import com.ofss.repository.*;

@Service
public class TransactionServiceImpl implements TransactionService {

    @Autowired
    AccountDao accountDao;

    @Autowired
    TransactionDao transactionDao;

    @Autowired
    JournalEntryDao journalEntryDao;

    @Autowired
    LedgerEntryDao ledgerEntryDao;

    @Override
    public List<BankTransaction> getAll() {
        return transactionDao.findAll();
    }

    @Override
    public BankTransaction getById(String id) {
        return transactionDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
    }

    @Override
    public List<BankTransaction> byAccount(Long accountId) {
        return transactionDao.findBySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(accountId, accountId);
    }

    @Override
    public List<BankTransaction> byCustomer(Long customerId) {
        return transactionDao
                .findBySourceAccountCustomerIdOrDestinationAccountCustomerIdOrderByCreatedAtDesc(customerId, customerId);
    }

    @Override
    @Transactional
    public BankTransaction deposit(TransactionRequest request) {
        validateAmount(request.getAmount());

        Account destinationAccount = getActiveAccount(request.getDestinationAccountId());
        Account cashAccount = getCashAccount();

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
        createLedgerEntry(journalEntry, destinationAccount, LedgerEntryType.CREDIT, request.getAmount());

        return transaction;
    }

    @Override
    @Transactional
    public BankTransaction withdraw(TransactionRequest request) {
        validateAmount(request.getAmount());

        Account sourceAccount = getActiveAccount(request.getSourceAccountId());
        Account cashAccount = getCashAccount();

        if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BadRequestException("Insufficient customer balance");
        }

        if (cashAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BadRequestException("Insufficient cash in system account");
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

        Account sourceAccount = getActiveAccount(request.getSourceAccountId());
        Account destinationAccount = getActiveAccount(request.getDestinationAccountId());

        if (sourceAccount.getId().equals(destinationAccount.getId())) {
            throw new BadRequestException("Source and destination accounts must be different");
        }

        if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new BadRequestException("Insufficient balance");
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
        createLedgerEntry(journalEntry, destinationAccount, LedgerEntryType.CREDIT, request.getAmount());

        return transaction;
    }

    private Account getActiveAccount(Long accountId) {
        if (accountId == null) {
            throw new BadRequestException("Account id is required");
        }

        Account account = accountDao.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("Account must be ACTIVE");
        }

        return account;
    }

    private Account getCashAccount() {
        Account cashAccount = accountDao
                .findByAccountCategoryAndAccountType(AccountCategory.SYSTEM, AccountType.CASH)
                .orElseThrow(() -> new ResourceNotFoundException("System cash account not found"));

        if (cashAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("System cash account must be ACTIVE");
        }

        return cashAccount;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }
    }

    private JournalEntry createJournalEntry(BankTransaction transaction) {
        JournalEntry journalEntry = new JournalEntry();
        journalEntry.setTransaction(transaction);
        journalEntry.setEntryReference("JE-" + transaction.getId());
        journalEntry.setEntryType(JournalEntryType.TRANSACTION);
        journalEntry.setStatus(JournalEntryStatus.POSTED);

        return journalEntryDao.save(journalEntry);
    }

    private void createLedgerEntry(JournalEntry journalEntry, Account account,
            LedgerEntryType entryType, BigDecimal amount) {

        LedgerEntry ledgerEntry = new LedgerEntry();
        ledgerEntry.setJournalEntry(journalEntry);
        ledgerEntry.setAccount(account);
        ledgerEntry.setEntryType(entryType);
        ledgerEntry.setAmount(amount);

        ledgerEntryDao.save(ledgerEntry);
    }
}
