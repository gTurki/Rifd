package com.example.rifd.Advice;

import com.example.rifd.Api.ApiException;
import com.example.rifd.Api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLIntegrityConstraintViolationException;

@Slf4j
@org.springframework.web.bind.annotation.ControllerAdvice
public class ControllerAdvice {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse> apiException(ApiException exception) {
        return ResponseEntity.status(400).body(new ApiResponse(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> methodArgumentNotValidException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? "Invalid request field" : error.getDefaultMessage())
                .findFirst().orElse("Invalid request body.");
        return ResponseEntity.status(400).body(new ApiResponse(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse> constraintViolationException(ConstraintViolationException exception) {
        return ResponseEntity.status(400).body(new ApiResponse(exception.getMessage()));
    }

    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public ResponseEntity<ApiResponse> sqlIntegrityConstraintViolationException(SQLIntegrityConstraintViolationException exception) {
        return ResponseEntity.status(400).body(new ApiResponse("The operation conflicts with existing data or references."));
    }

    @ExceptionHandler(InvalidDataAccessResourceUsageException.class)
    public ResponseEntity<ApiResponse> invalidDataAccessResourceUsageException(InvalidDataAccessResourceUsageException exception) {
        log.error("Invalid database resource usage", exception);
        return ResponseEntity.status(400).body(new ApiResponse("Unable to complete the database operation."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse> dataIntegrityViolationException(DataIntegrityViolationException exception) {
        return ResponseEntity.status(400).body(new ApiResponse("The operation conflicts with existing data or references."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse> httpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(400).body(new ApiResponse(exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse> httpMessageNotReadableException(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(400).body(new ApiResponse("Request body is missing or contains invalid JSON or field values."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse> methodArgumentTypeMismatchException(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.status(400).body(new ApiResponse("Invalid value for parameter '" + exception.getName() + "'."));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse> handlerMethodValidationException(HandlerMethodValidationException exception) {
        return ResponseEntity.status(400).body(new ApiResponse("Request parameters are outside their allowed range."));
    }

    // two people changed the same task / staff member at the same moment (@Version)
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse> optimisticLockingFailureException(ObjectOptimisticLockingFailureException exception) {
        return ResponseEntity.status(400).body(new ApiResponse("The record was changed by someone else, please refresh and retry."));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse> runtimeException(RuntimeException exception) {
        log.error("Unexpected request failure", exception);
        return ResponseEntity.status(400).body(new ApiResponse("Unable to complete the request."));
    }
}
