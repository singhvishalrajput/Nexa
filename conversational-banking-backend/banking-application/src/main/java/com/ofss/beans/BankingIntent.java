package com.ofss.beans;

import java.math.BigDecimal;

public class BankingIntent {
	private ChatIntentType intent;
	private BigDecimal amount;
	private String accountNumber;
	private String sourceAccountNumber;
	private String destinationAccountNumber;

	public ChatIntentType getIntent() {
		return intent;
	}

	public void setIntent(ChatIntentType intent) {
		this.intent = intent;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public void setAccountNumber(String accountNumber) {
		this.accountNumber = accountNumber;
	}

	public String getSourceAccountNumber() {
		return sourceAccountNumber;
	}

	public void setSourceAccountNumber(String sourceAccountNumber) {
		this.sourceAccountNumber = sourceAccountNumber;
	}

	public String getDestinationAccountNumber() {
		return destinationAccountNumber;
	}

	public void setDestinationAccountNumber(String destinationAccountNumber) {
		this.destinationAccountNumber = destinationAccountNumber;
	}
}
