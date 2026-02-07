package com.agentframework.exception;

import com.agentframework.dto.ApiErrorResponse;
import com.agentframework.dto.FieldValidationError;
import com.agentframework.dto.ValidationErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<FieldValidationError> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldValidationError(
                        ((FieldError) error).getField(),
                        error.getDefaultMessage()
                ))
                .toList();

        ValidationErrorResponse response = new ValidationErrorResponse(
                LocalDateTime.now().toString(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                errors
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                LocalDateTime.now().toString(),
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "Insufficient permissions to access this resource"
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                LocalDateTime.now().toString(),
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Authentication required"
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);

        ApiErrorResponse response = new ApiErrorResponse(
                LocalDateTime.now().toString(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                ex.getMessage()
        );

        return ResponseEntity.internalServerError().body(response);
    }
}

