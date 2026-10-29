package com.personalrecords.api.academic;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OfficialGrade {
    O(10),
    A_PLUS(9),
    A(8),
    B_PLUS(7),
    B(6),
    C(5),
    U(0);

    private final int gradePoint;

    OfficialGrade(int gradePoint) {
        this.gradePoint = gradePoint;
    }

    public int getGradePoint() {
        return gradePoint;
    }

    @JsonValue
    public String getLabel() {
        return name().replace("_PLUS", "+");
    }

    @JsonCreator
    public static OfficialGrade fromLabel(String label) {
        if (label == null) {
            return null;
        }
        return switch (label.trim().toUpperCase(Locale.ROOT)) {
            case "O" -> O;
            case "A+" -> A_PLUS;
            case "A" -> A;
            case "B+" -> B_PLUS;
            case "B" -> B;
            case "C" -> C;
            case "U" -> U;
            default -> throw new IllegalArgumentException("Unsupported official grade");
        };
    }
}
