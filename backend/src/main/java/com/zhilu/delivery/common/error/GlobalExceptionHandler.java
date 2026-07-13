package com.zhilu.delivery.common.error;

import com.zhilu.delivery.common.api.ApiError;
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
