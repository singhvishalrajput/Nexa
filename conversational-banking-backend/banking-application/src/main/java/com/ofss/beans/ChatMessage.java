package com.ofss.beans;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.*;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "CONVERSATION_ID")
	@JsonIgnore
	private ChatConversation conversation;

	@Enumerated(EnumType.STRING)
	private MessageSender sender;

	@Lob
	private String messageText;

	private LocalDateTime createdAt;

	@PrePersist
	void created() {
		createdAt = LocalDateTime.now();
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

	public MessageSender getSender() {
		return sender;
	}

	public void setSender(MessageSender sender) {
		this.sender = sender;
	}

	public String getMessageText() {
		return messageText;
	}

	public void setMessageText(String messageText) {
		this.messageText = messageText;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
