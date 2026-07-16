package com.zhilu.delivery.common.error;

import com.zhilu.delivery.common.api.ApiError;
import com.zhilu.delivery.automation.AiNotConfiguredException;
import com.zhilu.delivery.document.OutlineException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(OutlineException.class)
  public ResponseEntity<ApiError> handleOutline(OutlineException exception) {
    HttpStatus status = exception.getType() == OutlineException.Type.INVALID_RESPONSE
        ? HttpStatus.BAD_GATEWAY : HttpStatus.SERVICE_UNAVAILABLE;
    ApiError body = new ApiError(
        "OUTLINE_" + exception.getType().name(),
        exception.getMessage(),
        UUID.randomUUID().toString(),
        null);
    return ResponseEntity.status(status).body(body);
  }

  @ExceptionHandler(AiNotConfiguredException.class)
  public ResponseEntity<ApiError> handleAiNotConfigured(AiNotConfiguredException exception) {
    ApiError body = new ApiError(
        "AI_NOT_CONFIGURED", exception.getMessage(), UUID.randomUUID().toString(), null);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ApiError> handleConflict(ConflictException exception) {
    ApiError body = new ApiError(
        "CONFLICT", exception.getMessage(), UUID.randomUUID().toString(), null);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(NotFoundException exception) {
    ApiError body = new ApiError(
        "NOT_FOUND", exception.getMessage(), UUID.randomUUID().toString(), null);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception) {
    ApiError body = new ApiError(
        "INVALID_ARGUMENT", exception.getMessage(), UUID.randomUUID().toString(), null);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiError> handleAuthentication(AuthenticationException exception) {
    ApiError body = new ApiError(
        "BAD_CREDENTIALS",
        "用户名或密码错误",
        UUID.randomUUID().toString(),
        null);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
    Map<String, String> fieldErrors = new LinkedHashMap<String, String>();
    for (FieldError error : exception.getBindingResult().getFieldErrors()) {
      if (!fieldErrors.containsKey(error.getField())) {
        fieldErrors.put(error.getField(), error.getDefaultMessage());
      }
    }
    ApiError body = new ApiError(
        "VALIDATION_ERROR",
        "请求参数不正确",
        UUID.randomUUID().toString(),
        fieldErrors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }
}
