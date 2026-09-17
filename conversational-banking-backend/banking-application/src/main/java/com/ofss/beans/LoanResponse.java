package com.ofss.beans;

import java.math.BigDecimal;
import java.time.*;

public class LoanResponse {
	private Long id;
	private Long accountId;
	private String purpose;
	private String currencyCode;
	private BigDecimal amount;
	private BigDecimal annualInterestRate;
	private Integer tenureMonths;
	private BigDecimal emiAmount;
	private BigDecimal outstandingAmount;
	private String status;
	private LocalDate nextDueDate;
	private LocalDateTime createdAt;
	private LocalDateTime approvedAt;
	private LocalDateTime closedAt;

	public static LoanResponse from(Loan loan, LocalDate today) {
		LoanResponse response = new LoanResponse();
		response.id = loan.getId();
		response.accountId = loan.getAccount().getId();
		response.purpose = loan.getPurpose();
		response.currencyCode = "INR";
		response.amount = loan.getAmount();
		response.annualInterestRate = loan.getInterestRate();
		response.tenureMonths = loan.getTenureMonths();
		response.emiAmount = loan.getEmiAmount();
		response.outstandingAmount = loan.getOutstandingAmount();
		response.status = loan.getStatus() == LoanStatus.ACTIVE && loan.getNextDueDate().isBefore(today)
				? "OVERDUE" : loan.getStatus().name();
		response.nextDueDate = loan.getNextDueDate();
		response.createdAt = loan.getCreatedAt();
		response.approvedAt = loan.getApprovedAt();
		response.closedAt = loan.getClosedAt();
		return response;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long v) {
		id = v;
	}

	public Long getAccountId() {
		return accountId;
	}

	public void setAccountId(Long v) {
		accountId = v;
	}

	public String getPurpose() {
		return purpose;
	}

	public void setPurpose(String v) {
		purpose = v;
	}

	public String getCurrencyCode() {
		return currencyCode;
	}

	public void setCurrencyCode(String v) {
		currencyCode = v;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal v) {
		amount = v;
	}

	public BigDecimal getAnnualInterestRate() {
		return annualInterestRate;
	}

	public void setAnnualInterestRate(BigDecimal v) {
		annualInterestRate = v;
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

	public String getStatus() {
		return status;
	}

	public void setStatus(String v) {
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
}
