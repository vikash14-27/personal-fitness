package com.fitness.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

public record ApiError(
    String error,
    String message,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> fields) {

  public ApiError(String error, String message) {
    this(error, message, Map.of());
  }
}
