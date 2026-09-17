package com.ofss.beans;

import java.math.BigDecimal;
import java.time.*;

public class LoanQuoteRequest {
	private BigDecimal amount;
	private Integer tenureMonths;


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
