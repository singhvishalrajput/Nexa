package com.ofss.beans;

import java.math.BigDecimal;
import java.time.*;
import jakarta.persistence.*;

@Entity
@Table(name = "loan_payments")
public class LoanPayment {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "LOAN_ID", nullable = false)
	private Loan loan;

	@ManyToOne
	@JoinColumn(name = "INSTALLMENT_ID", nullable = true)
	private LoanInstallment installment;

	@ManyToOne
	@JoinColumn(name = "ACCOUNT_ID", nullable = false)
	private Account account;

	@Column(length = 80, nullable = false, unique = true)
	private String paymentReference;

	@Enumerated(EnumType.STRING)
	@Column(length = 24, nullable = false)
	private LoanPaymentType paymentType;

	@Column(precision = 19, scale = 2, nullable = false)
	private BigDecimal amount;

	@Column(precision = 19, scale = 2, nullable = false)
	private BigDecimal principalAmount;

	@Column(precision = 19, scale = 2, nullable = false)
	private BigDecimal interestAmount;

	@Enumerated(EnumType.STRING)
	@Column(length = 24, nullable = false)
	private LoanPaymentStatus status;

	@Column(nullable = false)
	private LocalDateTime paidAt;


	public Long getId() {
		return id;
	}

	public void setId(Long v) {
		id = v;
	}

	public Loan getLoan() {
		return loan;
	}

	public void setLoan(Loan v) {
		loan = v;
	}

	public LoanInstallment getInstallment() {
		return installment;
	}

	public void setInstallment(LoanInstallment v) {
		installment = v;
	}

	public Account getAccount() {
		return account;
	}

	public void setAccount(Account v) {
		account = v;
	}

	public String getPaymentReference() {
		return paymentReference;
	}

	public void setPaymentReference(String v) {
		paymentReference = v;
	}

	public LoanPaymentType getPaymentType() {
		return paymentType;
	}

	public void setPaymentType(LoanPaymentType v) {
		paymentType = v;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal v) {
		amount = v;
	}

	public BigDecimal getPrincipalAmount() {
		return principalAmount;
	}

	public void setPrincipalAmount(BigDecimal v) {
		principalAmount = v;
	}

	public BigDecimal getInterestAmount() {
		return interestAmount;
	}

	public void setInterestAmount(BigDecimal v) {
		interestAmount = v;
	}

	public LoanPaymentStatus getStatus() {
		return status;
	}

	public void setStatus(LoanPaymentStatus v) {
		status = v;
	}

	public LocalDateTime getPaidAt() {
		return paidAt;
	}

	public void setPaidAt(LocalDateTime v) {
		paidAt = v;
	}
}
