package com.ofss.beans;

public class ChatResponse {
	private ChatMessage customerMessage;
	private ChatMessage assistantMessage;

	public ChatResponse(ChatMessage customerMessage, ChatMessage assistantMessage) {
		this.customerMessage = customerMessage;
		this.assistantMessage = assistantMessage;
	}

	public ChatMessage getCustomerMessage() {
		return customerMessage;
	}

	public ChatMessage getAssistantMessage() {
		return assistantMessage;
	}
}
