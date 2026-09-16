package com.ofss.controller;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.ofss.beans.Customer;
import com.ofss.service.CustomerService;
import com.ofss.service.AuthorizationService;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
	@Autowired
	CustomerService service;

	@Autowired
	AuthorizationService authorizationService;

	@PostMapping
	public ResponseEntity<Customer> create(@RequestBody Customer c, Authentication authentication) {
		authorizationService.requireAdmin(authentication);
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(c));
	}

	@GetMapping
	public List<Customer> all(Authentication authentication) {
		authorizationService.requireAdmin(authentication);
		return service.getAll();
	}

	@GetMapping("/{id}")
	public Customer one(@PathVariable Long id, Authentication authentication) {
		authorizationService.requireCustomer(id, authentication);
		return service.getById(id);
	}

	@GetMapping("/email/{email}")
	public Customer email(@PathVariable String email, Authentication authentication) {
		Customer customer = service.getByEmail(email);
		authorizationService.requireCustomer(customer.getId(), authentication);
		return customer;
	}

	@GetMapping("/search")
	public List<Customer> search(@RequestParam String name, Authentication authentication) {
		authorizationService.requireAdmin(authentication);
		return service.search(name);
	}

	@GetMapping("/registered")
	public List<Customer> registered(@RequestParam LocalDate from, @RequestParam LocalDate to,
			Authentication authentication) {
		authorizationService.requireAdmin(authentication);
		return service.registered(from, to);
	}

	@PutMapping("/{id}")
	public Customer update(@PathVariable Long id, @RequestBody Customer c, Authentication authentication) {
		authorizationService.requireCustomer(id, authentication);
		return service.update(id, c);
	}
}
