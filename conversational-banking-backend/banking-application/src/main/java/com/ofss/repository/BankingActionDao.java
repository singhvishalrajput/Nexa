package com.ofss.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.ofss.beans.*;

import jakarta.persistence.LockModeType;

public interface BankingActionDao extends JpaRepository<BankingAction, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<BankingAction> findFirstByConversationIdAndCustomerIdAndStatusOrderByCreatedAtDesc(
			Long conversationId, Long customerId, BankingActionStatus status);
}
