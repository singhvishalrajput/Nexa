package com.ofss.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.ofss.beans.*;

import tools.jackson.databind.ObjectMapper;

@Service
public class OllamaServiceImpl implements OllamaService {
	@Autowired
	RestClient ollamaRestClient;

	@Autowired
	ObjectMapper objectMapper;

	@Value("${app.ollama.chat-model}")
	String model;

	@Value("${app.ollama.keep-alive}")
	int keepAlive;

	@Override
	public void warmUp() {
		OllamaGenerateRequest request = new OllamaGenerateRequest();
		request.setModel(model);
		request.setPrompt("");
		request.setStream(false);
		request.setKeepAlive(keepAlive);

		ollamaRestClient.post()
				.uri("/api/generate")
				.body(request)
				.retrieve()
				.toBodilessEntity();
	}

	@Override
	public BankingIntent understandMessage(String message) {
		OllamaChatRequest request = new OllamaChatRequest();
		request.setModel(model);
		request.setMessages(List.of(
				new OllamaChatMessage("system", systemPrompt()),
				new OllamaChatMessage("user", message)));
		request.setStream(false);
		request.setThink(false);
		request.setFormat("json");
		request.setKeepAlive(keepAlive);

		OllamaOptions options = new OllamaOptions();
		options.setTemperature(0);
		request.setOptions(options);

		OllamaChatResponse response = ollamaRestClient.post()
				.uri("/api/chat")
				.body(request)
				.retrieve()
				.body(OllamaChatResponse.class);

		if (response == null || response.getMessage() == null
				|| response.getMessage().getContent() == null
				|| response.getMessage().getContent().isBlank())
			throw new IllegalStateException("Ollama returned an empty response");

		BankingIntent intent = objectMapper.readValue(response.getMessage().getContent(), BankingIntent.class);
		if (intent.getIntent() == null)
			intent.setIntent(ChatIntentType.UNKNOWN);

		intent.setAccountNumber(normalizeAccountNumber(intent.getAccountNumber()));
		intent.setSourceAccountNumber(normalizeAccountNumber(intent.getSourceAccountNumber()));
		intent.setDestinationAccountNumber(normalizeAccountNumber(intent.getDestinationAccountNumber()));
		return intent;
	}

	private String normalizeAccountNumber(String accountNumber) {
		if (accountNumber == null || accountNumber.isBlank())
			return null;
		return accountNumber.trim().toUpperCase();
	}

	private String systemPrompt() {
		return """
				You classify messages for a banking application. Return only one valid JSON object and no other text.

				The JSON must always contain exactly these fields:
				{"intent":"UNKNOWN","amount":null,"accountNumber":null,"sourceAccountNumber":null,"destinationAccountNumber":null}

				Allowed intent values:
				GREETING, HELP, SHOW_ACCOUNTS, SHOW_BALANCE, SHOW_TRANSACTIONS,
				DEPOSIT, WITHDRAWAL, TRANSFER, CONFIRM, CANCEL, UNKNOWN.

				Rules:
				- Use SHOW_ACCOUNTS when the user asks to see or list accounts.
				- Use SHOW_BALANCE when the user asks for balances. Put a mentioned account in accountNumber.
				- Use SHOW_TRANSACTIONS when the user asks for transactions. Put a mentioned account in accountNumber.
				- For DEPOSIT, put the amount in amount and the receiving account in destinationAccountNumber.
				- For WITHDRAWAL, put the amount in amount and the account in sourceAccountNumber.
				- For TRANSFER, put the amount in amount, the sending account in sourceAccountNumber,
				  and the receiving account in destinationAccountNumber.
				- Use CONFIRM for a clear yes or confirmation and CANCEL for a clear no or cancellation.
				- Use UNKNOWN for questions unrelated to banking or unsupported requests.
				- Never invent an amount or account number. Use null when a value was not provided.
				- Amount must be a JSON number without a currency symbol.
				- Never claim that an operation succeeded and never answer the user's question.
				""";
	}
}
