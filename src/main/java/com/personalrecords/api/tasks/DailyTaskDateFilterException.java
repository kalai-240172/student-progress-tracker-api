package com.personalrecords.api.tasks;

public class DailyTaskDateFilterException extends RuntimeException {

    private final String field;

    public DailyTaskDateFilterException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
