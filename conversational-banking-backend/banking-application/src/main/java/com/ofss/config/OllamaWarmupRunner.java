package com.ofss.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.ofss.service.OllamaService;

@Component
public class OllamaWarmupRunner implements ApplicationRunner {
	@Autowired
	OllamaService ollamaService;

	@Override
	public void run(ApplicationArguments args) {
		try {
			System.out.println("Loading Ollama model into memory...");
			ollamaService.warmUp();
			System.out.println("Ollama model loaded successfully.");
		} catch (Exception ex) {
			System.out.println("Ollama model could not be loaded: " + ex.getMessage());
		}
	}
}
