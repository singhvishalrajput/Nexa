package com.ofss.beans;

import java.math.BigDecimal;
import java.time.*;

public class LoanQuoteResponse {
	private BigDecimal amount;
	private String currencyCode;
	private BigDecimal annualInterestRate;
	private Integer tenureMonths;
	private BigDecimal emiAmount;
	private BigDecimal finalEmiAmount;
	private BigDecimal totalInterest;
	private BigDecimal totalRepayment;


	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal v) {
		amount = v;
	}

	public String getCurrencyCode() {
		return currencyCode;
	}

	public void setCurrencyCode(String v) {
		currencyCode = v;
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

	public BigDecimal getFinalEmiAmount() {
		return finalEmiAmount;
	}

	public void setFinalEmiAmount(BigDecimal v) {
		finalEmiAmount = v;
	}

	public BigDecimal getTotalInterest() {
		return totalInterest;
	}

	public void setTotalInterest(BigDecimal v) {
		totalInterest = v;
	}

	public BigDecimal getTotalRepayment() {
		return totalRepayment;
	}

	public void setTotalRepayment(BigDecimal v) {
		totalRepayment = v;
	}
}
