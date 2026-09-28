package com.fitness.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiError> handleApi(ApiException ex) {
    return ResponseEntity.status(ex.getStatus())
        .body(new ApiError(ex.getError(), ex.getMessage(), ex.getFields()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getBindingResult().getFieldErrors().forEach(error ->
        fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return ResponseEntity.badRequest()
        .body(new ApiError("validation_failed", "Check the highlighted fields.", fields));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
    return ResponseEntity.badRequest()
        .body(new ApiError(
            "invalid_body",
            "The request body is invalid. Check dates, choices, and numeric fields."));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    String message = "day".equals(ex.getName())
        ? "Use a date like 2026-09-28."
        : "The request is invalid.";
    return ResponseEntity.badRequest().body(new ApiError("invalid_request", message));
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<ApiError> handleData(DataAccessException ex) {
    log.error("Database error", ex);
    return ResponseEntity.status(503)
        .body(new ApiError("database_unavailable", "The database is unavailable. Try again shortly."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
    log.error("Unhandled error", ex);
    return ResponseEntity.internalServerError()
        .body(new ApiError("internal_error", "Something went wrong."));
  }
}
