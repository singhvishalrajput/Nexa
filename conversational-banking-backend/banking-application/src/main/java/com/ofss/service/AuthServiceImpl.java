package com.ofss.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.*;
import com.ofss.exep.*;
import com.ofss.repository.CustomerCredentialDao;

@Service
public class AuthServiceImpl implements AuthService {
	@Autowired
	CustomerService customerService;

	@Autowired
	CustomerCredentialDao credentialDao;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JwtService jwtService;

	@Override
	@Transactional
	public LoginResponse register(RegisterRequest request) {
		validatePassword(request.getPassword());

		Customer customer = new Customer();
		customer.setFullName(request.getFullName());
		customer.setEmail(request.getEmail());
		customer.setPhoneNumber(request.getPhoneNumber());
		customer.setDateOfBirth(request.getDateOfBirth());
		customer.setAddress(request.getAddress());
		customer = customerService.create(customer);

		CustomerCredential credential = new CustomerCredential();
		credential.setCustomer(customer);
		credential.setPasswordHash(passwordEncoder.encode(request.getPassword()));
		credential.setRole(UserRole.CUSTOMER);
		credential = credentialDao.save(credential);

		return createLoginResponse(credential);
	}

	@Override
	public LoginResponse login(LoginRequest request) {
		if (request.getEmail() == null || request.getEmail().isBlank()
				|| request.getPassword() == null || request.getPassword().isBlank())
			throw new UnauthorizedException("Invalid email or password");

		CustomerCredential credential = credentialDao.findByCustomerEmail(request.getEmail())
				.orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

		if (!passwordEncoder.matches(request.getPassword(), credential.getPasswordHash()))
			throw new UnauthorizedException("Invalid email or password");

		return createLoginResponse(credential);
	}

	private LoginResponse createLoginResponse(CustomerCredential credential) {
		String token = jwtService.generateToken(credential);
		return new LoginResponse(token, jwtService.getExpirationSeconds(), credential.getCustomerId(),
				credential.getRole());
	}

	private void validatePassword(String password) {
		if (password == null || password.length() < 8)
			throw new BadRequestException("Password must contain at least 8 characters");
	}
}
