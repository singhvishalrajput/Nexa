package com.nexa.api.controller;
import com.nexa.api.beans.Customer;
import com.nexa.api.service.CustomerService;


import com.nexa.api.beans.Customer;
import com.nexa.api.service.CustomerService;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
  @Autowired CustomerService service;

  @PostMapping
  public ResponseEntity<Customer> create(@RequestBody Customer c) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(c));
  }

  @GetMapping
  public List<Customer> all() {
    return service.getAll();
  }

  @GetMapping("/{id}")
  public Customer one(@PathVariable Long id) {
    return service.getById(id);
  }

  @GetMapping("/email/{email}")
  public Customer email(@PathVariable String email) {
    return service.getByEmail(email);
  }

  @GetMapping("/search")
  public List<Customer> search(@RequestParam String name) {
    return service.search(name);
  }

  @GetMapping("/registered")
  public List<Customer> registered(@RequestParam LocalDate from, @RequestParam LocalDate to) {
    return service.registered(from, to);
  }

  @PutMapping("/{id}")
  public Customer update(@PathVariable Long id, @RequestBody Customer c) {
    return service.update(id, c);
  }
}
