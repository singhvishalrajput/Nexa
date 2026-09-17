package com.ofss.beans;

import java.math.BigDecimal;
import java.time.*;

public class LoanApplicationRequest {
	private Long accountId;
	private String applicationKey;
	private String purpose;
	private BigDecimal amount;
	private Integer tenureMonths;


	public Long getAccountId() {
		return accountId;
	}

	public void setAccountId(Long v) {
		accountId = v;
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

	public Integer getTenureMonths() {
		return tenureMonths;
	}

	public void setTenureMonths(Integer v) {
		tenureMonths = v;
	}
}
