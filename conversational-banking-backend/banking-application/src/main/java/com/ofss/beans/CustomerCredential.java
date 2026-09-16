package com.ofss.beans;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

@Entity
@Table(name = "customer_credentials")
public class CustomerCredential {
	@Id
	private Long customerId;

	@OneToOne
	@MapsId
	@JoinColumn(name = "CUSTOMER_ID")
	private Customer customer;

	@JsonIgnore
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	private UserRole role = UserRole.CUSTOMER;

	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	@PrePersist
	void created() {
		createdAt = updatedAt = LocalDateTime.now();
		if (role == null)
			role = UserRole.CUSTOMER;
	}

	@PreUpdate
	void updated() {
		updatedAt = LocalDateTime.now();
	}

	public Long getCustomerId() {
		return customerId;
	}

	public Customer getCustomer() {
		return customer;
	}

	public void setCustomer(Customer customer) {
		this.customer = customer;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public UserRole getRole() {
		return role;
	}

	public void setRole(UserRole role) {
		this.role = role;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
