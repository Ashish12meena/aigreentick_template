package com.aigreentick.services.template.exceptions;

public class InvalidMediaType extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidMediaType(String message) {
        super(message);
    }

    public InvalidMediaType(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidMediaType(Throwable cause) {
        super(cause);
    }
}
