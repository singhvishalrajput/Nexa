package com.ofss.beans;

import java.math.BigDecimal;
import java.time.*;
import jakarta.persistence.*;

@Entity
@Table(name = "loan_installments")
public class LoanInstallment {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "LOAN_ID", nullable = false)
	private Loan loan;

	@Column(nullable = false)
	private Integer installmentNumber;

	@Column(nullable = false)
	private LocalDate dueDate;

	@Column(precision = 19, scale = 2, nullable = false)
	private BigDecimal principalAmount;

	@Column(precision = 19, scale = 2, nullable = false)
	private BigDecimal interestAmount;

	@Column(precision = 19, scale = 2, nullable = false)
	private BigDecimal totalAmount;

	@Enumerated(EnumType.STRING)
	@Column(length = 24, nullable = false)
	private LoanInstallmentStatus status;

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

	public Integer getInstallmentNumber() {
		return installmentNumber;
	}

	public void setInstallmentNumber(Integer v) {
		installmentNumber = v;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public void setDueDate(LocalDate v) {
		dueDate = v;
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

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public void setTotalAmount(BigDecimal v) {
		totalAmount = v;
	}

	public LoanInstallmentStatus getStatus() {
		return status;
	}

	public void setStatus(LoanInstallmentStatus v) {
		status = v;
	}

	public LocalDateTime getPaidAt() {
		return paidAt;
	}

	public void setPaidAt(LocalDateTime v) {
		paidAt = v;
	}
}
