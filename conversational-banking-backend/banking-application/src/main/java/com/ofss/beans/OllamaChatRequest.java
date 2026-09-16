package com.ofss.beans;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OllamaChatRequest {
	private String model;
	private List<OllamaChatMessage> messages;
	private boolean stream;
	private boolean think;
	private String format;
	private OllamaOptions options;

	@JsonProperty("keep_alive")
	private int keepAlive;

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public List<OllamaChatMessage> getMessages() {
		return messages;
	}

	public void setMessages(List<OllamaChatMessage> messages) {
		this.messages = messages;
	}

	public boolean isStream() {
		return stream;
	}

	public void setStream(boolean stream) {
		this.stream = stream;
	}

	public boolean isThink() {
		return think;
	}

	public void setThink(boolean think) {
		this.think = think;
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public OllamaOptions getOptions() {
		return options;
	}

	public void setOptions(OllamaOptions options) {
		this.options = options;
	}

	public int getKeepAlive() {
		return keepAlive;
	}

	public void setKeepAlive(int keepAlive) {
		this.keepAlive = keepAlive;
	}
}
