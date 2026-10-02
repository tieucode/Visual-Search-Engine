package com.visualsearch.indexing.exception;

public class AiServiceException extends RuntimeException {

    private final boolean retryable;

    public AiServiceException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public AiServiceException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
