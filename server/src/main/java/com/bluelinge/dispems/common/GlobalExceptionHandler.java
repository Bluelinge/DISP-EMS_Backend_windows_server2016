package com.bluelinge.dispems.common;

import java.util.HashMap;
import java.util.Map;
import javax.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  @ExceptionHandler(AppException.class)
  public ResponseEntity<ApiResponse<Void>> handleApp(AppException e) {
    return ResponseEntity.status(e.getStatus()).body(ApiResponse.<Void>error(e.getCode(), e.getMessage()));
  }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
    Map<String, Object> body = new HashMap<String, Object>();
    FieldError fieldError = e.getBindingResult().getFieldError();
    body.put("code", 1001);
    body.put("message", fieldError == null ? "请求参数不合法" : fieldError.getDefaultMessage());
    body.put("data", null);
    body.put("requestId", RequestContext.requestId());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.<Void>error(1001, e.getMessage()));
  }
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
    log.error("Unexpected request failure, requestId={}", RequestContext.requestId(), e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.<Void>error(1500, "服务暂时不可用"));
  }
}
