package com.ofss.beans;

public class LoginResponse {
	private String token;
	private String tokenType;
	private long expiresInSeconds;
	private Long customerId;
	private UserRole role;

	public LoginResponse(String token, long expiresInSeconds, Long customerId, UserRole role) {
		this.token = token;
		this.tokenType = "Bearer";
		this.expiresInSeconds = expiresInSeconds;
		this.customerId = customerId;
		this.role = role;
	}

	public String getToken() {
		return token;
	}

	public String getTokenType() {
		return tokenType;
	}

	public long getExpiresInSeconds() {
		return expiresInSeconds;
	}

	public Long getCustomerId() {
		return customerId;
	}

	public UserRole getRole() {
		return role;
	}
}
