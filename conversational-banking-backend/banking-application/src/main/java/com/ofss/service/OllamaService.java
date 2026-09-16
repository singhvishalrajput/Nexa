package com.ofss.service;

import com.ofss.beans.BankingIntent;

public interface OllamaService {
	void warmUp();

	BankingIntent understandMessage(String message);
}
