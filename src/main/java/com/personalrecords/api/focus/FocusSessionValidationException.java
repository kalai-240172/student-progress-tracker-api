package com.personalrecords.api.focus;

public class FocusSessionValidationException extends RuntimeException {

    private final String field;

    public FocusSessionValidationException(String message) {
        this("session", message);
    }

    public FocusSessionValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
