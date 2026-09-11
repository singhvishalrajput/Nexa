package com.nexa.api.core.service;

import com.nexa.api.core.model.*;
import com.nexa.api.core.repository.*;
import com.nexa.api.shared.errors.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AccountServiceImpl implements AccountService {
  @Autowired AccountDao accounts;

  @Autowired CustomerDao customers;

  @Autowired LedgerEntryDao ledgers;

  @Autowired TransactionDao transactions;

  public Account create(Account a) {
    if (accounts.existsByAccountNumber(a.getAccountNumber()))
      throw new InvalidRequestException("Account number already exists");
    if (a.getAccountCategory() == null)
      throw new InvalidRequestException("Account category is required");
    if (a.getAccountCategory() == AccountCategory.CUSTOMER) {
      if (a.getCustomer() == null || a.getCustomer().getId() == null)
        throw new InvalidRequestException("Customer account requires customer.id");
      a.setCustomer(
          customers
              .findById(a.getCustomer().getId())
              .orElseThrow(() -> new ResourceNotFoundException("Customer not found")));
    } else if (a.getCustomer() != null)
      throw new InvalidRequestException("System account cannot have a customer");
    return accounts.save(a);
  }

  public List<Account> getAll() {
    return accounts.findAll();
  }

  public Account getById(Long id) {
    return accounts
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
  }

  public Account getByNumber(String n) {
    return accounts
        .findByAccountNumber(n)
        .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + n));
  }

  public List<Account> byCustomer(Long id) {
    return accounts.findByCustomerId(id);
  }

  public List<Account> byStatus(AccountStatus s) {
    return accounts.findByStatus(s);
  }

  public Account update(Long id, Account input) {
    Account a = getById(id);
    a.setAccountName(input.getAccountName());
    a.setStatus(input.getStatus());
    return accounts.save(a);
  }

  public List<LedgerEntry> ledger(Long id) {
    getById(id);
    return ledgers.findByAccountId(id);
  }

  public List<BankTransaction> transactions(Long id) {
    getById(id);
    return transactions.findBySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(id, id);
  }

  @Override
  public List<Account> getAccountsByStatusCreatedWithinDays(AccountStatus status, int days) {
    if (days < 0) {
      throw new InvalidRequestException("Days cannot be negative");
    }

    LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);

    return accounts.findByStatusAndCreatedAtAfter(status, cutoffDate);
  }
}
