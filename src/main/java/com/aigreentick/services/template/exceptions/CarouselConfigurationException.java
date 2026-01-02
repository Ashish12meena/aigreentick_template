package com.aigreentick.services.template.exceptions;


public class CarouselConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CarouselConfigurationException(String message) {
        super(message);
    }

    public CarouselConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public CarouselConfigurationException(Throwable cause) {
        super(cause);
    }
}
