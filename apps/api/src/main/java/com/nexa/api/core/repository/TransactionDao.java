package com.nexa.api.core.repository;

import com.nexa.api.core.model.BankTransaction;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionDao
    extends JpaRepository<BankTransaction, String>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<BankTransaction> {
  List<BankTransaction> findBySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(
      Long sourceId, Long destinationId);
}
