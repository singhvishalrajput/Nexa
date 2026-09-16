package com.nexa.api.repository;
import com.nexa.api.beans.BankTransaction;


import com.nexa.api.beans.BankTransaction;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionDao
    extends JpaRepository<BankTransaction, String>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<BankTransaction> {
  List<BankTransaction> findBySourceAccountIdOrDestinationAccountIdOrderByCreatedAtDesc(
      Long sourceId, Long destinationId);
}
