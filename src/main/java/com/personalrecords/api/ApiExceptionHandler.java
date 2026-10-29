package com.personalrecords.api;

import java.util.LinkedHashMap;
import java.util.Map;

import com.personalrecords.api.goals.GoalNotFoundException;
import com.personalrecords.api.goals.ProgressLogNotFoundException;
import com.personalrecords.api.records.StudentRecordNotFoundException;
import com.personalrecords.api.user.DuplicateEmailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> {
            String field = switch (error.getField()) {
                case "passwordWithinBcryptLimit" -> "password";
                case "dateRangeValid" -> "dateRange";
                default -> error.getField();
            };
            fieldErrors.putIfAbsent(field, error.getDefaultMessage());
        });
        exception.getBindingResult().getGlobalErrors().forEach(error -> {
            String field = error.getDefaultMessage().startsWith("End date")
                    ? "dateRange" : "password";
            fieldErrors.putIfAbsent(field, error.getDefaultMessage());
        });
        return response(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST, "Request body is missing or malformed", Map.of());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiError> handleDuplicateEmail(DuplicateEmailException exception) {
        return response(HttpStatus.CONFLICT, "An account with this email already exists", Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "The requested resource was not found", Map.of());
    }

    @ExceptionHandler(StudentRecordNotFoundException.class)
    public ResponseEntity<ApiError> handleStudentRecordNotFound(StudentRecordNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "The requested record was not found", Map.of());
    }

    @ExceptionHandler({GoalNotFoundException.class, ProgressLogNotFoundException.class})
    public ResponseEntity<ApiError> handleGoalResourceNotFound(RuntimeException exception) {
        return response(HttpStatus.NOT_FOUND, "The requested resource was not found", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        logger.error("Unexpected error while handling API request", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", Map.of());
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status, String message, Map<String, String> fieldErrors) {
        ApiError body = new ApiError(status.value(), status.getReasonPhrase(), message, fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
