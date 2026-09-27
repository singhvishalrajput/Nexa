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

  @org.springframework.data.jpa.repository.Query("""
      select distinct lower(trim(t.category)) from BankTransaction t
      where (t.sourceAccount.id = :accountId or t.destinationAccount.id = :accountId)
        and t.category is not null and length(trim(t.category)) > 0
      order by lower(trim(t.category))
      """)
  List<String> findCategoriesForAccount(@org.springframework.data.repository.query.Param("accountId") Long accountId);
}
