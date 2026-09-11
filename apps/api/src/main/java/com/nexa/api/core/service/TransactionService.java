package com.nexa.api.core.service;

import com.nexa.api.core.model.*;
import java.util.*;

public interface TransactionService {
  BankTransaction deposit(TransactionRequest request);

  BankTransaction withdraw(TransactionRequest request);

  BankTransaction transfer(TransactionRequest request);

  List<BankTransaction> getAll();

  BankTransaction getById(String id);

  List<BankTransaction> byAccount(Long accountId);
}
