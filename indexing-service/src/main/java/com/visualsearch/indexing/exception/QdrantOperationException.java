package com.visualsearch.indexing.exception;

public class QdrantOperationException extends RuntimeException {

    private final boolean retryable;

    public QdrantOperationException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public QdrantOperationException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
