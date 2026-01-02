package com.aigreentick.services.template.exceptions;



public class InvalidTemplateComponentType extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidTemplateComponentType(String message) {
        super(message);
    }

    public InvalidTemplateComponentType(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidTemplateComponentType(Throwable cause) {
        super(cause);
    }
}
