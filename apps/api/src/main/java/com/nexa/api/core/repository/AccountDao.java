package com.nexa.api.core.repository;

import com.nexa.api.core.model.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountDao extends JpaRepository<Account, Long> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select a from Account a where a.id = :id")
  Optional<Account> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);

  List<Account> findByCustomerUserIdOrderByCreatedAtAsc(String userId);

  Optional<Account> findByIdAndCustomerUserId(Long id, String userId);

  Optional<Account> findByAccountNumber(String number);

  List<Account> findByCustomerId(Long customerId);

  List<Account> findByStatus(AccountStatus status);

  boolean existsByAccountNumber(String accountNumber);

  List<Account> findByStatusAndCreatedAtAfter(AccountStatus status, LocalDateTime cutoffDate);

  Optional<Account> findByAccountCategoryAndAccountType(
      AccountCategory accountCategory, AccountType accountType);
}
