package com.ofss.repository;

import java.util.*;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ofss.beans.ChatConversation;

public interface ChatConversationDao extends JpaRepository<ChatConversation, Long> {
	List<ChatConversation> findByCustomerIdOrderByUpdatedAtDesc(Long customerId);

	Optional<ChatConversation> findByIdAndCustomerId(Long id, Long customerId);
}
