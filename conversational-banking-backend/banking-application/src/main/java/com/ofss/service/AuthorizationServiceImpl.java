package com.ofss.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.ofss.beans.*;

@Service
public class AuthorizationServiceImpl implements AuthorizationService {
	@Override
	public Long getCustomerId(Authentication authentication) {
		try {
			return Long.valueOf(authentication.getName());
		} catch (Exception e) {
			throw new AccessDeniedException("Authenticated customer is invalid");
		}
	}

	@Override
	public boolean isAdmin(Authentication authentication) {
		return authentication.getAuthorities().stream()
				.anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
	}

	@Override
	public void requireAdmin(Authentication authentication) {
		if (!isAdmin(authentication))
			throw new AccessDeniedException("Administrator access is required");
	}

	@Override
	public void requireCustomer(Long customerId, Authentication authentication) {
		if (!isAdmin(authentication) && !getCustomerId(authentication).equals(customerId))
			throw new AccessDeniedException("You cannot access another customer's data");
	}

	@Override
	public void requireAccount(Account account, Authentication authentication) {
		if (isAdmin(authentication))
			return;
		if (account.getCustomer() == null
				|| !getCustomerId(authentication).equals(account.getCustomer().getId()))
			throw new AccessDeniedException("You cannot access this account");
	}

	@Override
	public void requireTransaction(BankTransaction transaction, Authentication authentication) {
		if (isAdmin(authentication))
			return;

		Long customerId = getCustomerId(authentication);
		boolean ownsSource = transaction.getSourceAccount() != null
				&& transaction.getSourceAccount().getCustomer() != null
				&& customerId.equals(transaction.getSourceAccount().getCustomer().getId());
		boolean ownsDestination = transaction.getDestinationAccount() != null
				&& transaction.getDestinationAccount().getCustomer() != null
				&& customerId.equals(transaction.getDestinationAccount().getCustomer().getId());

		if (!ownsSource && !ownsDestination)
			throw new AccessDeniedException("You cannot access this transaction");
	}
}
