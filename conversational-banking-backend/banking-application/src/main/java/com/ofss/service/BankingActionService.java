package com.ofss.service;

import java.math.BigDecimal;

import com.ofss.beans.ChatConversation;

public interface BankingActionService {
	String requestDeposit(ChatConversation conversation, Long customerId, String destinationAccountNumber,
			BigDecimal amount);

	String requestWithdrawal(ChatConversation conversation, Long customerId, String sourceAccountNumber,
			BigDecimal amount);

	String requestTransfer(ChatConversation conversation, Long customerId, String sourceAccountNumber,
			String destinationAccountNumber, BigDecimal amount);

	String confirm(Long conversationId, Long customerId);

	String cancel(Long conversationId, Long customerId);
}
