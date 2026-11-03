package com.personalrecords.api.focus;

public class FocusSessionDateFilterException extends RuntimeException {

    public String getField() {
        return "startDate";
    }

    @Override
    public String getMessage() {
        return "Both startDate and endDate are required, and startDate must be on or before endDate.";
    }
}
