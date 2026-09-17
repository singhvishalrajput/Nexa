package com.ofss.beans;

import java.math.BigDecimal;
import java.time.*;

public class LoanInstallmentResponse {
	private Long id;
	private Long loanId;
	private Integer installmentNumber;
	private LocalDate dueDate;
	private BigDecimal principalAmount;
	private BigDecimal interestAmount;
	private BigDecimal totalAmount;
	private String status;
	private LocalDateTime paidAt;

	public static LoanInstallmentResponse from(LoanInstallment installment, LocalDate today) {
		LoanInstallmentResponse response = new LoanInstallmentResponse();
		response.id = installment.getId();
		response.loanId = installment.getLoan().getId();
		response.installmentNumber = installment.getInstallmentNumber();
		response.dueDate = installment.getDueDate();
		response.principalAmount = installment.getPrincipalAmount();
		response.interestAmount = installment.getInterestAmount();
		response.totalAmount = installment.getTotalAmount();
		response.status = installment.getStatus() == LoanInstallmentStatus.PENDING && installment.getDueDate().isBefore(today)
				? "OVERDUE" : installment.getStatus().name();
		response.paidAt = installment.getPaidAt();
		return response;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long v) {
		id = v;
	}

	public Long getLoanId() {
		return loanId;
	}

	public void setLoanId(Long v) {
		loanId = v;
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

	public String getStatus() {
		return status;
	}

	public void setStatus(String v) {
		status = v;
	}

	public LocalDateTime getPaidAt() {
		return paidAt;
	}

	public void setPaidAt(LocalDateTime v) {
		paidAt = v;
	}
}
