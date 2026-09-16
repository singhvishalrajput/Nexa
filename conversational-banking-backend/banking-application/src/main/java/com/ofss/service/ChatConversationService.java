package com.ofss.service;

import java.util.List;

import com.ofss.beans.ChatConversation;

public interface ChatConversationService {
	ChatConversation create(Long customerId, String title);

	List<ChatConversation> getAll(Long customerId);

	ChatConversation getById(Long conversationId, Long customerId);

	ChatConversation close(Long conversationId, Long customerId);
}
