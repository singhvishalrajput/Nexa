package com.ofss.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ofss.beans.*;
import com.ofss.exep.ResourceNotFoundException;
import com.ofss.repository.*;

@Service
public class ChatConversationServiceImpl implements ChatConversationService {
	@Autowired
	ChatConversationDao conversationDao;

	@Autowired
	CustomerDao customerDao;

	@Override
	public ChatConversation create(Long customerId, String title) {
		if (title != null && title.trim().length() > 150)
			throw new com.ofss.exep.BadRequestException("Title cannot exceed 150 characters");

		Customer customer = customerDao.findById(customerId)
				.orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));

		ChatConversation conversation = new ChatConversation();
		conversation.setCustomer(customer);
		conversation.setTitle(title == null || title.isBlank() ? "New Conversation" : title.trim());
		conversation.setStatus(ConversationStatus.ACTIVE);
		return conversationDao.save(conversation);
	}

	@Override
	public List<ChatConversation> getAll(Long customerId) {
		return conversationDao.findByCustomerIdOrderByUpdatedAtDesc(customerId);
	}

	@Override
	public ChatConversation getById(Long conversationId, Long customerId) {
		return conversationDao.findByIdAndCustomerId(conversationId, customerId)
				.orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));
	}

	@Override
	public ChatConversation close(Long conversationId, Long customerId) {
		ChatConversation conversation = getById(conversationId, customerId);
		conversation.setStatus(ConversationStatus.CLOSED);
		return conversationDao.save(conversation);
	}
}
