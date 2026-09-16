package com.ofss.beans;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

@Entity
@Table(name = "transactions")
public class BankTransaction {
	@Id
	private String id;
	@Enumerated(EnumType.STRING)
	private TransactionType transactionType;
	@ManyToOne
	@JoinColumn(name = "SOURCE_ACCOUNT_ID")
	@JsonIgnoreProperties("customer")
	private Account sourceAccount;
	@ManyToOne
	@JoinColumn(name = "DESTINATION_ACCOUNT_ID")
	@JsonIgnoreProperties("customer")
	private Account destinationAccount;
	private BigDecimal amount;
	@Enumerated(EnumType.STRING)
	private TransactionStatus status;
	private LocalDateTime createdAt;
	private LocalDateTime completedAt;

	@PrePersist
	void created() {
		if (id == null) {
			id = "TX-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
		}
		createdAt = LocalDateTime.now();
	}

	public String getId() {
		return id;
	}

	public TransactionType getTransactionType() {
		return transactionType;
	}

	public void setTransactionType(TransactionType v) {
		transactionType = v;
	}

	public Account getSourceAccount() {
		return sourceAccount;
	}

	public void setSourceAccount(Account v) {
		sourceAccount = v;
	}

	public Account getDestinationAccount() {
		return destinationAccount;
	}

	public void setDestinationAccount(Account v) {
		destinationAccount = v;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal v) {
		amount = v;
	}

	public TransactionStatus getStatus() {
		return status;
	}

	public void setStatus(TransactionStatus v) {
		status = v;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(LocalDateTime v) {
		completedAt = v;
	}
}
