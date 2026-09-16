package com.ofss.beans;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

@Entity
@Table(name = "chat_conversations")
public class ChatConversation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "CUSTOMER_ID")
	@JsonIgnore
	private Customer customer;

	private String title;

	@Enumerated(EnumType.STRING)
	private ConversationStatus status = ConversationStatus.ACTIVE;

	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	@PrePersist
	void created() {
		createdAt = updatedAt = LocalDateTime.now();
		if (status == null)
			status = ConversationStatus.ACTIVE;
	}

	@PreUpdate
	void updated() {
		updatedAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public Customer getCustomer() {
		return customer;
	}

	public void setCustomer(Customer customer) {
		this.customer = customer;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public ConversationStatus getStatus() {
		return status;
	}

	public void setStatus(ConversationStatus status) {
		this.status = status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void touch() {
		updatedAt = LocalDateTime.now();
	}
}
