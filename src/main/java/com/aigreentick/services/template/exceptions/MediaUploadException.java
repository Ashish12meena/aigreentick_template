package com.aigreentick.services.template.exceptions;

import java.io.IOException;

public class MediaUploadException extends RuntimeException {
    public MediaUploadException(String message) {
        super(message);
    }

    public MediaUploadException(String message, IOException retryEx) {
        super(message);
    }
}
