package com.ofss.service;

import java.util.Collection;
import java.util.List;
import com.ofss.beans.Account;

public interface AccountLockService {
	Account lockAccount(Long id);

	List<Account> lockAccounts(Collection<Long> ids);
}
