package com.personalrecords.api.exports;

public class ExportFilterException extends RuntimeException {

    private final String field;

    public ExportFilterException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
