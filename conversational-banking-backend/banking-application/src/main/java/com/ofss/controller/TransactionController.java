package com.ofss.controller;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.ofss.beans.*;
import com.ofss.service.TransactionService;
import com.ofss.service.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

	@Autowired
	TransactionService service;

	@Autowired
	AuthorizationService authorizationService;

	@Autowired
	AccountService accountService;

	@PostMapping("/deposit")
	public ResponseEntity<BankTransaction> deposit(@RequestBody TransactionRequest r, Authentication authentication) {
		authorizationService.requireAccount(accountService.getById(r.getDestinationAccountId()), authentication);
		return ResponseEntity.status(HttpStatus.CREATED).body(service.deposit(r));
	}

	@PostMapping("/withdraw")
	public ResponseEntity<BankTransaction> withdraw(@RequestBody TransactionRequest r, Authentication authentication) {
		authorizationService.requireAccount(accountService.getById(r.getSourceAccountId()), authentication);
		return ResponseEntity.status(HttpStatus.CREATED).body(service.withdraw(r));
	}

	@PostMapping("/transfer")
	public ResponseEntity<BankTransaction> transfer(@RequestBody TransactionRequest r, Authentication authentication) {
		authorizationService.requireAccount(accountService.getById(r.getSourceAccountId()), authentication);
		return ResponseEntity.status(HttpStatus.CREATED).body(service.transfer(r));
	}

	@GetMapping
	public List<BankTransaction> all(Authentication authentication) {
		if (authorizationService.isAdmin(authentication))
			return service.getAll();
		return service.byCustomer(authorizationService.getCustomerId(authentication));
	}

	@GetMapping("/{id}")
	public BankTransaction one(@PathVariable String id, Authentication authentication) {
		BankTransaction transaction = service.getById(id);
		authorizationService.requireTransaction(transaction, authentication);
		return transaction;
	}

	@GetMapping("/account/{accountId}")
	public List<BankTransaction> account(@PathVariable Long accountId, Authentication authentication) {
		authorizationService.requireAccount(accountService.getById(accountId), authentication);
		return service.byAccount(accountId);
	}
}
