package com.fitness.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String error;
  private final Map<String, String> fields;

  public ApiException(HttpStatus status, String error, String message) {
    this(status, error, message, Map.of());
  }

  public ApiException(HttpStatus status, String error, String message, Map<String, String> fields) {
    super(message);
    this.status = status;
    this.error = error;
    this.fields = fields == null ? Map.of() : Map.copyOf(fields);
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getError() {
    return error;
  }

  public Map<String, String> getFields() {
    return fields;
  }
}
