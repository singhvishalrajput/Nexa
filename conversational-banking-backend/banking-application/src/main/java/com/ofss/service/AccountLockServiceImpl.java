package com.ofss.service;

import java.util.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.ofss.beans.Account;
import com.ofss.exep.*;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AccountLockServiceImpl implements AccountLockService {
	@PersistenceContext
	EntityManager entityManager;

	@Override
	public Account lockAccount(Long id) {
		return lockAccounts(Collections.singletonList(id)).get(0);
	}

	@Override
	public List<Account> lockAccounts(Collection<Long> ids) {
		if (ids == null || ids.isEmpty())
			throw new BadRequestException("At least one account id is required");
		SortedSet<Long> orderedIds = new TreeSet<>();
		for (Long id : ids) {
			if (id == null || id <= 0)
				throw new BadRequestException("Account id must be greater than zero");
			orderedIds.add(id);
		}

		List<Account> accounts = new ArrayList<>();
		for (Long id : orderedIds) {
			Account account = entityManager.find(Account.class, id, LockModeType.PESSIMISTIC_WRITE);
			if (account == null)
				throw new ResourceNotFoundException("Account not found: " + id);
			// Authorization may already have loaded this account into the request session.
			// Refresh after locking so every balance change uses the current database value.
			entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
			accounts.add(account);
		}
		return accounts;
	}
}
