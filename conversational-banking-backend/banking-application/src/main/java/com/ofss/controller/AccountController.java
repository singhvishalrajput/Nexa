package com.ofss.controller;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.ofss.beans.*;
import com.ofss.service.AccountService;
import com.ofss.service.AuthorizationService;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
	@Autowired
	AccountService service;

	@Autowired
	AuthorizationService authorizationService;

	@PostMapping
	public ResponseEntity<Account> create(@RequestBody Account a, Authentication authentication) {
		if (authorizationService.isAdmin(authentication)) {
			return ResponseEntity.status(HttpStatus.CREATED).body(service.create(a));
		}

		if (a.getAccountCategory() != AccountCategory.CUSTOMER)
			throw new org.springframework.security.access.AccessDeniedException(
					"Customers can create only CUSTOMER accounts");

		Customer customer = new Customer();
		customer.setId(authorizationService.getCustomerId(authentication));
		a.setCustomer(customer);
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(a));
	}

	@GetMapping
	public List<Account> all(Authentication authentication) {
		if (authorizationService.isAdmin(authentication))
			return service.getAll();
		return service.byCustomer(authorizationService.getCustomerId(authentication));
	}

	@GetMapping("/{id}")
	public Account one(@PathVariable Long id, Authentication authentication) {
		Account account = service.getById(id);
		authorizationService.requireAccount(account, authentication);
		return account;
	}

	@GetMapping("/number/{accountNumber}")
	public Account number(@PathVariable String accountNumber, Authentication authentication) {
		Account account = service.getByNumber(accountNumber);
		authorizationService.requireAccount(account, authentication);
		return account;
	}

	@GetMapping("/customer/{customerId}")
	public List<Account> customer(@PathVariable Long customerId, Authentication authentication) {
		authorizationService.requireCustomer(customerId, authentication);
		return service.byCustomer(customerId);
	}

	@GetMapping("/status/{status}")
	public List<Account> status(@PathVariable AccountStatus status, Authentication authentication) {
		authorizationService.requireAdmin(authentication);
		return service.byStatus(status);
	}

	@PutMapping("/{id}")
	public Account update(@PathVariable Long id, @RequestBody Account a, Authentication authentication) {
		authorizationService.requireAccount(service.getById(id), authentication);
		return service.update(id, a);
	}

	@GetMapping("/{id}/ledger")
	public List<LedgerEntry> ledger(@PathVariable Long id, Authentication authentication) {
		authorizationService.requireAccount(service.getById(id), authentication);
		return service.ledger(id);
	}

	@GetMapping("/{id}/transactions")
	public List<BankTransaction> transactions(@PathVariable Long id, Authentication authentication) {
		authorizationService.requireAccount(service.getById(id), authentication);
		return service.transactions(id);
	}
	
	@GetMapping("/{status}/{days}")
	public List<Account> getAccountsByStatusAndCreatedDays(
	        @PathVariable AccountStatus status,
	        @PathVariable int days,
	        Authentication authentication
	) {
		authorizationService.requireAdmin(authentication);
	    return service.getAccountsByStatusCreatedWithinDays(status, days);
	}
}
