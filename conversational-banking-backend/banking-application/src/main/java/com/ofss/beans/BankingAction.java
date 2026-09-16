package com.ofss.beans;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

@Entity
@Table(name = "banking_actions")
public class BankingAction {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "CONVERSATION_ID")
	@JsonIgnore
	private ChatConversation conversation;

	@ManyToOne
	@JoinColumn(name = "CUSTOMER_ID")
	@JsonIgnore
	private Customer customer;

	@Enumerated(EnumType.STRING)
	private BankingActionType actionType;

	@ManyToOne
	@JoinColumn(name = "SOURCE_ACCOUNT_ID")
	private Account sourceAccount;

	@ManyToOne
	@JoinColumn(name = "DESTINATION_ACCOUNT_ID")
	private Account destinationAccount;

	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	private BankingActionStatus status = BankingActionStatus.PENDING_CONFIRMATION;

	@OneToOne
	@JoinColumn(name = "TRANSACTION_ID")
	private BankTransaction transaction;

	private LocalDateTime createdAt;
	private LocalDateTime confirmedAt;

	@PrePersist
	void created() {
		createdAt = LocalDateTime.now();
		if (status == null)
			status = BankingActionStatus.PENDING_CONFIRMATION;
	}

	public Long getId() {
		return id;
	}

	public ChatConversation getConversation() {
		return conversation;
	}

	public void setConversation(ChatConversation conversation) {
		this.conversation = conversation;
	}

	public Customer getCustomer() {
		return customer;
	}

	public void setCustomer(Customer customer) {
		this.customer = customer;
	}

	public BankingActionType getActionType() {
		return actionType;
	}

	public void setActionType(BankingActionType actionType) {
		this.actionType = actionType;
	}

	public Account getSourceAccount() {
		return sourceAccount;
	}

	public void setSourceAccount(Account sourceAccount) {
		this.sourceAccount = sourceAccount;
	}

	public Account getDestinationAccount() {
		return destinationAccount;
	}

	public void setDestinationAccount(Account destinationAccount) {
		this.destinationAccount = destinationAccount;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public BankingActionStatus getStatus() {
		return status;
	}

	public void setStatus(BankingActionStatus status) {
		this.status = status;
	}

	public BankTransaction getTransaction() {
		return transaction;
	}

	public void setTransaction(BankTransaction transaction) {
		this.transaction = transaction;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getConfirmedAt() {
		return confirmedAt;
	}

	public void setConfirmedAt(LocalDateTime confirmedAt) {
		this.confirmedAt = confirmedAt;
	}
}
