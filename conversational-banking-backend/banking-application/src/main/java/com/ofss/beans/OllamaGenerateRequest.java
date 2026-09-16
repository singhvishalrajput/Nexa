package com.ofss.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OllamaGenerateRequest {
	private String model;
	private String prompt;
	private boolean stream;

	@JsonProperty("keep_alive")
	private int keepAlive;

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getPrompt() {
		return prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}

	public boolean isStream() {
		return stream;
	}

	public void setStream(boolean stream) {
		this.stream = stream;
	}

	public int getKeepAlive() {
		return keepAlive;
	}

	public void setKeepAlive(int keepAlive) {
		this.keepAlive = keepAlive;
	}
}
