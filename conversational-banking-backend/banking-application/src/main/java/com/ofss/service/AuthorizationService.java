package com.ofss.service;

import org.springframework.security.core.Authentication;

import com.ofss.beans.*;

public interface AuthorizationService {
	Long getCustomerId(Authentication authentication);

	boolean isAdmin(Authentication authentication);

	void requireAdmin(Authentication authentication);

	void requireCustomer(Long customerId, Authentication authentication);

	void requireAccount(Account account, Authentication authentication);

	void requireTransaction(BankTransaction transaction, Authentication authentication);
}
