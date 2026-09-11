package com.nexa.api.core.service;

import com.nexa.api.core.model.*;
import java.util.*;

public interface AccountService {
  Account create(Account account);

  List<Account> getAll();

  Account getById(Long id);

  Account getByNumber(String number);

  List<Account> byCustomer(Long customerId);

  List<Account> byStatus(AccountStatus status);

  Account update(Long id, Account input);

  List<LedgerEntry> ledger(Long id);

  List<BankTransaction> transactions(Long id);

  List<Account> getAccountsByStatusCreatedWithinDays(AccountStatus status, int days);
}
