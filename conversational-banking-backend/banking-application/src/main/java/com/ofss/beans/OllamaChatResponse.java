package com.ofss.beans;

public class OllamaChatResponse {
	private OllamaChatMessage message;
	private boolean done;

	public OllamaChatMessage getMessage() {
		return message;
	}

	public void setMessage(OllamaChatMessage message) {
		this.message = message;
	}

	public boolean isDone() {
		return done;
	}

	public void setDone(boolean done) {
		this.done = done;
	}
}
