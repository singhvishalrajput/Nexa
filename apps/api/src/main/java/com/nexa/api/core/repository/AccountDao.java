package com.nexa.api.core.repository;

import com.nexa.api.core.model.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountDao extends JpaRepository<Account, Long> {
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
