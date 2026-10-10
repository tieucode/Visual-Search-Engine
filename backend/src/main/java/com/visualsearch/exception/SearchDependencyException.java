package com.visualsearch.exception;

/** A search dependency failed or returned an invalid response. */
public class SearchDependencyException extends RuntimeException {
    public SearchDependencyException(String message) {
        super(message);
    }

    public SearchDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
