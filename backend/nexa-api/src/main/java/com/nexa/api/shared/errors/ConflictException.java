package com.nexa.api.shared.errors;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
