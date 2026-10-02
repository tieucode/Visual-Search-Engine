package com.visualsearch.indexing.exception;

public class AiCircuitOpenException extends RuntimeException {
    public AiCircuitOpenException() {
        super("AI service circuit is open");
    }
}
