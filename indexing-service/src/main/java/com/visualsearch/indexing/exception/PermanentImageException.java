package com.visualsearch.indexing.exception;

public class PermanentImageException extends RuntimeException {
    public PermanentImageException(String message) {
        super(message);
    }

    public PermanentImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
