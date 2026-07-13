package com.zhilu.delivery.common.api;

import java.util.Collections;
import java.util.Map;

public final class ApiError {
  private final String code;
  private final String message;
  private final String traceId;
  private final Map<String, String> fieldErrors;

  public ApiError(
      String code, String message, String traceId, Map<String, String> fieldErrors) {
    this.code = code;
    this.message = message;
    this.traceId = traceId;
    this.fieldErrors = fieldErrors == null
        ? Collections.<String, String>emptyMap()
        : Collections.unmodifiableMap(fieldErrors);
  }

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  public String getTraceId() {
    return traceId;
  }

  public Map<String, String> getFieldErrors() {
    return fieldErrors;
  }
}
