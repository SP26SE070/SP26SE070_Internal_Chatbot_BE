package com.gsp26se114.chatbot_rag_be.exception;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
