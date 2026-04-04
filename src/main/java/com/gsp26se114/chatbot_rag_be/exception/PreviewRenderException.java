package com.gsp26se114.chatbot_rag_be.exception;

public class PreviewRenderException extends RuntimeException {
    public PreviewRenderException(String message) {
        super(message);
    }

    public PreviewRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
