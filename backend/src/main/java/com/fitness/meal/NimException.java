package com.fitness.meal;

public class NimException extends RuntimeException {

  private final String userMessage;

  public NimException(String userMessage) {
    super(userMessage);
    this.userMessage = userMessage;
  }

  public NimException(String userMessage, Throwable cause) {
    super(userMessage, cause);
    this.userMessage = userMessage;
  }

  public String getUserMessage() {
    return userMessage;
  }
}
