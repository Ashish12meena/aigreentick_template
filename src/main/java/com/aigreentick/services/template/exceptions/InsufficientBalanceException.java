package com.aigreentick.services.template.exceptions;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String message, int i) {
        super(message);
    }
}
