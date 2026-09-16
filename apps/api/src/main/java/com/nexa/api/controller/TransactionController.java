package com.nexa.api.controller;
import com.nexa.api.beans.Account;
import com.nexa.api.beans.BankTransaction;
import com.nexa.api.beans.TransactionRequest;
import com.nexa.api.service.TransactionService;

import com.nexa.api.service.TransactionService;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

  @Autowired TransactionService service;

  @PostMapping("/deposit")
  public ResponseEntity<BankTransaction> deposit(@RequestBody TransactionRequest r) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.deposit(r));
  }

  @PostMapping("/withdraw")
  public ResponseEntity<BankTransaction> withdraw(@RequestBody TransactionRequest r) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.withdraw(r));
  }

  @PostMapping("/transfer")
  public ResponseEntity<BankTransaction> transfer(@RequestBody TransactionRequest r) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.transfer(r));
  }

  @GetMapping
  public List<BankTransaction> all() {
    return service.getAll();
  }

  @GetMapping("/{id}")
  public BankTransaction one(@PathVariable String id) {
    return service.getById(id);
  }

  @GetMapping("/account/{accountId}")
  public List<BankTransaction> account(@PathVariable Long accountId) {
    return service.byAccount(accountId);
  }
}
