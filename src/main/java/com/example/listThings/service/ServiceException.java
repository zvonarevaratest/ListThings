package com.example.listThings.service;

public class ServiceException extends RuntimeException {

    public enum Kind {
        INVALID_INPUT, NOT_FOUND, DUPLICATE_NAME
    }

    private final Kind kind;

    ServiceException(Kind kind, String message) {
        this(kind, message, null);
    }

    ServiceException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
