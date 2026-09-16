package com.ofss.service;

import java.math.*;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.*;
import com.ofss.exep.*;
import com.ofss.repository.*;

@Service
public class BankingActionServiceImpl implements BankingActionService {
	@Autowired
	BankingActionDao actionDao;

	@Autowired
	AccountDao accountDao;

	@Autowired
	TransactionService transactionService;

	@Override
	@Transactional
	public String requestDeposit(ChatConversation conversation, Long customerId, String destinationAccountNumber,
			BigDecimal amount) {
		String pendingMessage = checkForPendingAction(conversation.getId(), customerId);
		if (pendingMessage != null)
			return pendingMessage;

		validateAmount(amount);
		Account destination = getOwnedActiveAccount(destinationAccountNumber, customerId);

		BankingAction action = createAction(conversation, BankingActionType.DEPOSIT, amount);
		action.setDestinationAccount(destination);
		actionDao.save(action);

		return "Please confirm:\nDeposit " + formatAmount(amount) + " into " + destination.getAccountNumber()
				+ ".\n\nReply CONFIRM to continue or CANCEL to stop.";
	}

	@Override
	@Transactional
	public String requestWithdrawal(ChatConversation conversation, Long customerId, String sourceAccountNumber,
			BigDecimal amount) {
		String pendingMessage = checkForPendingAction(conversation.getId(), customerId);
		if (pendingMessage != null)
			return pendingMessage;

		validateAmount(amount);
		Account source = getOwnedActiveAccount(sourceAccountNumber, customerId);

		BankingAction action = createAction(conversation, BankingActionType.WITHDRAWAL, amount);
		action.setSourceAccount(source);
		actionDao.save(action);

		return "Please confirm:\nWithdraw " + formatAmount(amount) + " from " + source.getAccountNumber()
				+ ".\n\nReply CONFIRM to continue or CANCEL to stop.";
	}

	@Override
	@Transactional
	public String requestTransfer(ChatConversation conversation, Long customerId, String sourceAccountNumber,
			String destinationAccountNumber, BigDecimal amount) {
		String pendingMessage = checkForPendingAction(conversation.getId(), customerId);
		if (pendingMessage != null)
			return pendingMessage;

		validateAmount(amount);
		Account source = getOwnedActiveAccount(sourceAccountNumber, customerId);
		Account destination = getActiveDestinationAccount(destinationAccountNumber);

		if (source.getId().equals(destination.getId()))
			throw new BadRequestException("Source and destination accounts must be different");

		BankingAction action = createAction(conversation, BankingActionType.TRANSFER, amount);
		action.setSourceAccount(source);
		action.setDestinationAccount(destination);
		actionDao.save(action);

		return "Please confirm:\nTransfer " + formatAmount(amount) + "\nFrom: " + source.getAccountNumber()
				+ "\nTo: " + destination.getAccountNumber()
				+ "\n\nReply CONFIRM to continue or CANCEL to stop.";
	}

	@Override
	@Transactional
	public String confirm(Long conversationId, Long customerId) {
		Optional<BankingAction> pending = findPendingAction(conversationId, customerId);
		if (pending.isEmpty())
			return "There is no banking action waiting for confirmation.";

		BankingAction action = pending.get();
		TransactionRequest request = new TransactionRequest();
		request.setAmount(action.getAmount());

		BankTransaction transaction;
		switch (action.getActionType()) {
		case DEPOSIT:
			request.setDestinationAccountId(action.getDestinationAccount().getId());
			transaction = transactionService.deposit(request);
			break;
		case WITHDRAWAL:
			request.setSourceAccountId(action.getSourceAccount().getId());
			transaction = transactionService.withdraw(request);
			break;
		case TRANSFER:
			request.setSourceAccountId(action.getSourceAccount().getId());
			request.setDestinationAccountId(action.getDestinationAccount().getId());
			transaction = transactionService.transfer(request);
			break;
		default:
			throw new BadRequestException("Unsupported banking action");
		}

		action.setTransaction(transaction);
		action.setStatus(BankingActionStatus.COMPLETED);
		action.setConfirmedAt(LocalDateTime.now());
		actionDao.save(action);

		return action.getActionType() + " successful.\nTransaction ID: " + transaction.getId();
	}

	@Override
	@Transactional
	public String cancel(Long conversationId, Long customerId) {
		Optional<BankingAction> pending = findPendingAction(conversationId, customerId);
		if (pending.isEmpty())
			return "There is no banking action waiting for cancellation.";

		BankingAction action = pending.get();
		action.setStatus(BankingActionStatus.CANCELLED);
		actionDao.save(action);
		return action.getActionType() + " cancelled. No money was moved.";
	}

	private BankingAction createAction(ChatConversation conversation, BankingActionType type, BigDecimal amount) {
		BankingAction action = new BankingAction();
		action.setConversation(conversation);
		action.setCustomer(conversation.getCustomer());
		action.setActionType(type);
		action.setAmount(amount);
		action.setStatus(BankingActionStatus.PENDING_CONFIRMATION);
		return action;
	}

	private String checkForPendingAction(Long conversationId, Long customerId) {
		if (findPendingAction(conversationId, customerId).isPresent())
			return "You already have a banking action waiting for confirmation. Reply CONFIRM or CANCEL first.";
		return null;
	}

	private Optional<BankingAction> findPendingAction(Long conversationId, Long customerId) {
		return actionDao.findFirstByConversationIdAndCustomerIdAndStatusOrderByCreatedAtDesc(conversationId,
				customerId, BankingActionStatus.PENDING_CONFIRMATION);
	}

	private Account getOwnedActiveAccount(String accountNumber, Long customerId) {
		Account account = accountDao.findByAccountNumber(accountNumber)
				.orElseThrow(() -> new ResourceNotFoundException("Account was not found among your accounts"));

		if (account.getCustomer() == null || !customerId.equals(account.getCustomer().getId()))
			throw new ResourceNotFoundException("Account was not found among your accounts");
		validateActive(account);
		return account;
	}

	private Account getActiveDestinationAccount(String accountNumber) {
		Account account = accountDao.findByAccountNumber(accountNumber)
				.orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));
		if (account.getAccountCategory() != AccountCategory.CUSTOMER)
			throw new BadRequestException("Destination must be a customer account");
		validateActive(account);
		return account;
	}

	private void validateActive(Account account) {
		if (account.getStatus() != AccountStatus.ACTIVE)
			throw new BadRequestException("Account must be ACTIVE");
	}

	private void validateAmount(BigDecimal amount) {
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
			throw new BadRequestException("Amount must be greater than zero");
		if (amount.scale() > 2)
			throw new BadRequestException("Amount can have at most two decimal places");
	}

	private String formatAmount(BigDecimal amount) {
		return "Rs. " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}
}
