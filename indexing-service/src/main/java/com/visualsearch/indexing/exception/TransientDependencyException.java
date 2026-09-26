package com.visualsearch.indexing.exception;

public class TransientDependencyException extends RuntimeException {
    public TransientDependencyException(String message) {
        super(message);
    }

    public TransientDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
