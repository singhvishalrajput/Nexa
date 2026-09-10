package com.ofss.repository;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ofss.beans.*;

public interface AccountDao extends JpaRepository<Account, Long> {
	Optional<Account> findByAccountNumber(String number);

	List<Account> findByCustomerId(Long customerId);

	List<Account> findByStatus(AccountStatus status);

	boolean existsByAccountNumber(String accountNumber);

	List<Account> findByStatusAndCreatedAtAfter(AccountStatus status, LocalDateTime cutoffDate);

	Optional<Account> findByAccountCategoryAndAccountType(AccountCategory accountCategory, AccountType accountType);
}
