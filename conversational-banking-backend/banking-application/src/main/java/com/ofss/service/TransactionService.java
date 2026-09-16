package com.ofss.service;

import java.util.*;
import com.ofss.beans.*;

public interface TransactionService {
	BankTransaction deposit(TransactionRequest request);

	BankTransaction withdraw(TransactionRequest request);

	BankTransaction transfer(TransactionRequest request);

	List<BankTransaction> getAll();

	BankTransaction getById(String id);

	List<BankTransaction> byAccount(Long accountId);

	List<BankTransaction> byCustomer(Long customerId);
}
