package com.nexa.api.service;
import com.nexa.api.beans.BankTransaction;
import com.nexa.api.beans.TransactionRequest;

import java.util.*;

public interface TransactionService {
  BankTransaction deposit(TransactionRequest request);

  BankTransaction withdraw(TransactionRequest request);

  BankTransaction transfer(TransactionRequest request);

  List<BankTransaction> getAll();

  BankTransaction getById(String id);

  List<BankTransaction> byAccount(Long accountId);
}
