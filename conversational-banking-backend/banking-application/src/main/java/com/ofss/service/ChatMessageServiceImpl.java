package com.ofss.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ofss.beans.*;
import com.ofss.exep.BadRequestException;
import com.ofss.repository.*;

@Service
public class ChatMessageServiceImpl implements ChatMessageService {
	@Autowired
	ChatMessageDao messageDao;

	@Autowired
	ChatConversationDao conversationDao;

	@Autowired
	ChatConversationService conversationService;

	@Autowired
	AccountService accountService;

	@Autowired
	TransactionService transactionService;

	@Autowired
	BankingActionService bankingActionService;

	@Autowired
	OllamaService ollamaService;

	@Override
	@Transactional
	public ChatResponse send(Long conversationId, Long customerId, String message) {
		if (message == null || message.isBlank())
			throw new BadRequestException("Message is required");

		ChatConversation conversation = conversationService.getById(conversationId, customerId);
		if (conversation.getStatus() == ConversationStatus.CLOSED)
			throw new BadRequestException("Cannot send a message to a closed conversation");

		ChatMessage customerMessage = createMessage(conversation, MessageSender.CUSTOMER, message.trim());
		ChatMessage assistantMessage = createMessage(conversation, MessageSender.ASSISTANT,
				createAssistantReply(message, customerId, conversation));

		conversation.touch();
		conversationDao.save(conversation);
		return new ChatResponse(customerMessage, assistantMessage);
	}

	@Override
	public List<ChatMessage> getAll(Long conversationId, Long customerId) {
		conversationService.getById(conversationId, customerId);
		return messageDao.findByConversationIdOrderByIdAsc(conversationId);
	}

	private ChatMessage createMessage(ChatConversation conversation, MessageSender sender, String text) {
		ChatMessage message = new ChatMessage();
		message.setConversation(conversation);
		message.setSender(sender);
		message.setMessageText(text);
		return messageDao.save(message);
	}

	private String createAssistantReply(String message, Long customerId, ChatConversation conversation) {
		BankingIntent bankingIntent;
		try {
			bankingIntent = ollamaService.understandMessage(message);
		} catch (Exception ex) {
			return "The banking assistant is temporarily unavailable. Please try again.";
		}

		switch (bankingIntent.getIntent()) {
		case GREETING:
			return "Hello! How can I help you with your banking today?";
		case HELP:
			return "You can ask me to show your accounts, balances, or recent transactions. You can also request a deposit, withdrawal, or transfer, which must be confirmed before money moves.";
		case SHOW_ACCOUNTS:
			return showAccounts(customerId);
		case SHOW_BALANCE:
			return showBalance(customerId, bankingIntent.getAccountNumber());
		case SHOW_TRANSACTIONS:
			return showTransactions(customerId, bankingIntent.getAccountNumber());
		case DEPOSIT:
			return requestDeposit(bankingIntent, customerId, conversation);
		case WITHDRAWAL:
			return requestWithdrawal(bankingIntent, customerId, conversation);
		case TRANSFER:
			return requestTransfer(bankingIntent, customerId, conversation);
		case CONFIRM:
			return bankingActionService.confirm(conversation.getId(), customerId);
		case CANCEL:
			return bankingActionService.cancel(conversation.getId(), customerId);
		case UNKNOWN:
		default:
			return "I can help only with banking requests such as accounts, balances, transactions, deposits, withdrawals, and transfers.";
		}
	}

	private String requestDeposit(BankingIntent intent, Long customerId, ChatConversation conversation) {
		if (intent.getAmount() == null && isBlank(intent.getDestinationAccountNumber()))
			return "Please provide the amount and destination account number for the deposit.";
		if (intent.getAmount() == null)
			return "Please provide the deposit amount.";
		if (isBlank(intent.getDestinationAccountNumber()))
			return "Please provide the destination account number for the deposit.";

		return bankingActionService.requestDeposit(conversation, customerId,
				intent.getDestinationAccountNumber(), intent.getAmount());
	}

	private String requestWithdrawal(BankingIntent intent, Long customerId, ChatConversation conversation) {
		if (intent.getAmount() == null && isBlank(intent.getSourceAccountNumber()))
			return "Please provide the amount and account number for the withdrawal.";
		if (intent.getAmount() == null)
			return "Please provide the withdrawal amount.";
		if (isBlank(intent.getSourceAccountNumber()))
			return "Please provide the account number for the withdrawal.";

		return bankingActionService.requestWithdrawal(conversation, customerId,
				intent.getSourceAccountNumber(), intent.getAmount());
	}

	private String requestTransfer(BankingIntent intent, Long customerId, ChatConversation conversation) {
		if (intent.getAmount() == null)
			return "Please provide the transfer amount.";
		if (isBlank(intent.getSourceAccountNumber()) && isBlank(intent.getDestinationAccountNumber()))
			return "Please provide the source and destination account numbers for the transfer.";
		if (isBlank(intent.getSourceAccountNumber()))
			return "Please provide the source account number for the transfer.";
		if (isBlank(intent.getDestinationAccountNumber()))
			return "Please provide the destination account number for the transfer.";

		return bankingActionService.requestTransfer(conversation, customerId,
				intent.getSourceAccountNumber(), intent.getDestinationAccountNumber(), intent.getAmount());
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private String showAccounts(Long customerId) {
		List<Account> accounts = accountService.byCustomer(customerId);
		if (accounts.isEmpty())
			return "You do not have any accounts.";

		StringBuilder response = new StringBuilder("Your accounts:\n");
		for (int i = 0; i < accounts.size(); i++) {
			Account account = accounts.get(i);
			response.append("\n")
					.append(i + 1).append(". ")
					.append(account.getAccountNumber())
					.append(" - ").append(account.getAccountName())
					.append("\nType: ").append(account.getAccountType())
					.append("\nStatus: ").append(account.getStatus());
		}
		return response.toString();
	}

	private String showBalances(Long customerId) {
		List<Account> accounts = accountService.byCustomer(customerId);
		if (accounts.isEmpty())
			return "You do not have any accounts.";

		StringBuilder response = new StringBuilder("Your account balances:\n");
		for (int i = 0; i < accounts.size(); i++) {
			Account account = accounts.get(i);
			response.append("\n")
					.append(i + 1).append(". ")
					.append(account.getAccountNumber())
					.append(" - ").append(formatAmount(account.getBalance()));
		}
		return response.toString();
	}

	private String showBalance(Long customerId, String accountNumber) {
		if (isBlank(accountNumber))
			return showBalances(customerId);

		Account account = findOwnedAccount(customerId, accountNumber);
		if (account == null)
			return "I could not find that account among your accounts.";

		return "The balance of " + account.getAccountNumber() + " is "
				+ formatAmount(account.getBalance()) + ".";
	}

	private String showRecentTransactions(Long customerId) {
		List<BankTransaction> transactions = transactionService.byCustomer(customerId);
		if (transactions.isEmpty())
			return "You do not have any transactions.";

		StringBuilder response = new StringBuilder("Your recent transactions:\n");
		int numberToShow = Math.min(transactions.size(), 5);
		for (int i = 0; i < numberToShow; i++) {
			appendTransaction(response, transactions.get(i), i + 1);
		}
		return response.toString();
	}

	private String showTransactions(Long customerId, String accountNumber) {
		if (isBlank(accountNumber))
			return showRecentTransactions(customerId);

		Account account = findOwnedAccount(customerId, accountNumber);
		if (account == null)
			return "I could not find that account among your accounts.";
		return showAccountTransactions(account);
	}

	private String showAccountTransactions(Account account) {
		List<BankTransaction> transactions = transactionService.byAccount(account.getId());
		if (transactions.isEmpty())
			return "There are no transactions for account " + account.getAccountNumber() + ".";

		StringBuilder response = new StringBuilder("Recent transactions for ")
				.append(account.getAccountNumber()).append(":\n");
		int numberToShow = Math.min(transactions.size(), 5);
		for (int i = 0; i < numberToShow; i++) {
			appendTransaction(response, transactions.get(i), i + 1);
		}
		return response.toString();
	}

	private Account findOwnedAccount(Long customerId, String accountNumber) {
		List<Account> accounts = accountService.byCustomer(customerId);
		for (Account account : accounts) {
			if (account.getAccountNumber().equalsIgnoreCase(accountNumber))
				return account;
		}
		return null;
	}

	private void appendTransaction(StringBuilder response, BankTransaction transaction, int number) {
		response.append("\n")
				.append(number).append(". ")
				.append(transaction.getTransactionType())
				.append(" - ").append(formatAmount(transaction.getAmount()))
				.append(" - ").append(transaction.getStatus())
				.append("\nTransaction ID: ").append(transaction.getId());
	}

	private String formatAmount(BigDecimal amount) {
		return "Rs. " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}
}
