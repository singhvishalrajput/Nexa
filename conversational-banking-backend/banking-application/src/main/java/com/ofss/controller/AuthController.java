package com.ofss.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.ofss.beans.*;
import com.ofss.service.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
	@Autowired
	AuthService authService;

	@Autowired
	AuthorizationService authorizationService;

	@Autowired
	CustomerService customerService;

	@PostMapping("/register")
	public ResponseEntity<LoginResponse> register(@RequestBody RegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
	}

	@PostMapping("/login")
	public LoginResponse login(@RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@GetMapping("/me")
	public Customer me(Authentication authentication) {
		return customerService.getById(authorizationService.getCustomerId(authentication));
	}
}
