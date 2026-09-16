package com.ofss.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ofss.beans.ChatMessage;

public interface ChatMessageDao extends JpaRepository<ChatMessage, Long> {
	List<ChatMessage> findByConversationIdOrderByIdAsc(Long conversationId);
}
