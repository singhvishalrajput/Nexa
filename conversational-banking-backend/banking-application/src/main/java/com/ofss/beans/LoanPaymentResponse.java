package com.ofss.beans;

import java.math.BigDecimal;
import java.time.*;

public class LoanPaymentResponse {
	private Long id;
	private Long loanId;
	private Long installmentId;
	private Long accountId;
	private String reference;
	private LoanPaymentType type;
	private String currencyCode;
	private BigDecimal amount;
	private BigDecimal principalAmount;
	private BigDecimal interestAmount;
	private LoanPaymentStatus status;
	private LocalDateTime paidAt;

	public static LoanPaymentResponse from(LoanPayment payment) {
		LoanPaymentResponse response = new LoanPaymentResponse();
		response.id = payment.getId();
		response.loanId = payment.getLoan().getId();
		response.installmentId = payment.getInstallment() == null ? null : payment.getInstallment().getId();
		response.accountId = payment.getAccount().getId();
		response.reference = payment.getPaymentReference();
		response.type = payment.getPaymentType();
		response.currencyCode = "INR";
		response.amount = payment.getAmount();
		response.principalAmount = payment.getPrincipalAmount();
		response.interestAmount = payment.getInterestAmount();
		response.status = payment.getStatus();
		response.paidAt = payment.getPaidAt();
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

	public Long getInstallmentId() {
		return installmentId;
	}

	public void setInstallmentId(Long v) {
		installmentId = v;
	}

	public Long getAccountId() {
		return accountId;
	}

	public void setAccountId(Long v) {
		accountId = v;
	}

	public String getReference() {
		return reference;
	}

	public void setReference(String v) {
		reference = v;
	}

	public LoanPaymentType getType() {
		return type;
	}

	public void setType(LoanPaymentType v) {
		type = v;
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
