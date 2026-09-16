package com.ofss.service;

import java.util.List;

import com.ofss.beans.*;

public interface ChatMessageService {
	ChatResponse send(Long conversationId, Long customerId, String message);

	List<ChatMessage> getAll(Long conversationId, Long customerId);
}
