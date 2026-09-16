package com.nexa.api.controller;
import com.nexa.api.beans.Account;
import com.nexa.api.beans.AccountStatus;
import com.nexa.api.beans.BankTransaction;
import com.nexa.api.beans.Customer;
import com.nexa.api.beans.LedgerEntry;
import com.nexa.api.service.AccountService;

import com.nexa.api.service.AccountService;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
  @Autowired AccountService service;

  @PostMapping
  public ResponseEntity<Account> create(@RequestBody Account a) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(a));
  }

  @GetMapping
  public List<Account> all() {
    return service.getAll();
  }

  @GetMapping("/{id}")
  public Account one(@PathVariable Long id) {
    return service.getById(id);
  }

  @GetMapping("/number/{accountNumber}")
  public Account number(@PathVariable String accountNumber) {
    return service.getByNumber(accountNumber);
  }

  @GetMapping("/customer/{customerId}")
  public List<Account> customer(@PathVariable Long customerId) {
    return service.byCustomer(customerId);
  }

  @GetMapping("/status/{status}")
  public List<Account> status(@PathVariable AccountStatus status) {
    return service.byStatus(status);
  }

  @PutMapping("/{id}")
  public Account update(@PathVariable Long id, @RequestBody Account a) {
    return service.update(id, a);
  }

  @GetMapping("/{id}/ledger")
  public List<LedgerEntry> ledger(@PathVariable Long id) {
    return service.ledger(id);
  }

  @GetMapping("/{id}/transactions")
  public List<BankTransaction> transactions(@PathVariable Long id) {
    return service.transactions(id);
  }

  @GetMapping("/{status}/{days}")
  public List<Account> getAccountsByStatusAndCreatedDays(
      @PathVariable AccountStatus status, @PathVariable int days) {
    return service.getAccountsByStatusCreatedWithinDays(status, days);
  }
}
