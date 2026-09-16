package com.ofss.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ofss.beans.BankTransaction;

public interface TransactionDao extends JpaRepository<BankTransaction, String> {
	List<BankTransaction> findBySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(Long sourceId,
			Long destinationId);

	List<BankTransaction> findBySourceAccountCustomerIdOrDestinationAccountCustomerIdOrderByCreatedAtDesc(
			Long sourceCustomerId, Long destinationCustomerId);
}
