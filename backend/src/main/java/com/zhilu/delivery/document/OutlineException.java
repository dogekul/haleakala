package com.zhilu.delivery.document;

public class OutlineException extends RuntimeException {
  public enum Type {
    NOT_CONFIGURED,
    VALIDATION,
    AUTHENTICATION,
    FORBIDDEN,
    NOT_FOUND,
    RATE_LIMIT,
    TIMEOUT,
    UNAVAILABLE,
    INVALID_RESPONSE
  }

  private final Type type;

  public OutlineException(Type type, String message) {
    super(message);
    this.type = type;
  }

  public OutlineException(Type type, String message, Throwable cause) {
    super(message, cause);
    this.type = type;
  }

  public Type getType() {
    return type;
  }
}
