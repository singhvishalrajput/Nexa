package com.ofss.beans;

import java.math.BigDecimal;
import java.time.*;
import jakarta.persistence.*;

@Entity
@Table(name = "loans")
public class Loan {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "CUSTOMER_ID", nullable = false)
	private Customer customer;

	@ManyToOne
	@JoinColumn(name = "ACCOUNT_ID", nullable = false)
	private Account account;

	@Column(length = 80, nullable = false)
	private String applicationKey;

	@Column(length = 200, nullable = false)
	private String purpose;

	@Column(precision = 19, scale = 2, nullable = false)
	private BigDecimal amount;

	@Column(precision = 5, scale = 2, nullable = false)
	private BigDecimal interestRate;

	@Column(nullable = false)
	private Integer tenureMonths;

	@Column(precision = 19, scale = 2, nullable = false)
	private BigDecimal emiAmount;

	@Column(precision = 19, scale = 2, nullable = false)
	private BigDecimal outstandingAmount;

	@Enumerated(EnumType.STRING)
	@Column(length = 24, nullable = false)
	private LoanStatus status;

	private LocalDate nextDueDate;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime approvedAt;

	private LocalDateTime closedAt;

	@Version
	@Column(nullable = false)
	private Long version;


	public Long getId() {
		return id;
	}

	public void setId(Long v) {
		id = v;
	}

	public Customer getCustomer() {
		return customer;
	}

	public void setCustomer(Customer v) {
		customer = v;
	}

	public Account getAccount() {
		return account;
	}

	public void setAccount(Account v) {
		account = v;
	}

	public String getApplicationKey() {
		return applicationKey;
	}

	public void setApplicationKey(String v) {
		applicationKey = v;
	}

	public String getPurpose() {
		return purpose;
	}

	public void setPurpose(String v) {
		purpose = v;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal v) {
		amount = v;
	}

	public BigDecimal getInterestRate() {
		return interestRate;
	}

	public void setInterestRate(BigDecimal v) {
		interestRate = v;
	}

	public Integer getTenureMonths() {
		return tenureMonths;
	}

	public void setTenureMonths(Integer v) {
		tenureMonths = v;
	}

	public BigDecimal getEmiAmount() {
		return emiAmount;
	}

	public void setEmiAmount(BigDecimal v) {
		emiAmount = v;
	}

	public BigDecimal getOutstandingAmount() {
		return outstandingAmount;
	}

	public void setOutstandingAmount(BigDecimal v) {
		outstandingAmount = v;
	}

	public LoanStatus getStatus() {
		return status;
	}

	public void setStatus(LoanStatus v) {
		status = v;
	}

	public LocalDate getNextDueDate() {
		return nextDueDate;
	}

	public void setNextDueDate(LocalDate v) {
		nextDueDate = v;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime v) {
		createdAt = v;
	}

	public LocalDateTime getApprovedAt() {
		return approvedAt;
	}

	public void setApprovedAt(LocalDateTime v) {
		approvedAt = v;
	}

	public LocalDateTime getClosedAt() {
		return closedAt;
	}

	public void setClosedAt(LocalDateTime v) {
		closedAt = v;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long v) {
		version = v;
	}
}
