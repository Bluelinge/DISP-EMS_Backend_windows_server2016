package com.bluelinge.dispems.common;

import org.springframework.http.HttpStatus;

public class AppException extends RuntimeException {
  private final HttpStatus status;
  private final int code;
  public AppException(HttpStatus status, int code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }
  public HttpStatus getStatus() { return status; }
  public int getCode() { return code; }
  public static AppException badRequest(String message) { return new AppException(HttpStatus.BAD_REQUEST, 1001, message); }
  public static AppException unauthorized() { return new AppException(HttpStatus.UNAUTHORIZED, 1002, "登录已过期，请重新登录"); }
  public static AppException forbidden(String message) { return new AppException(HttpStatus.FORBIDDEN, 1003, message); }
  public static AppException notFound(String message) { return new AppException(HttpStatus.NOT_FOUND, 1004, message); }
  public static AppException conflict(String message) { return new AppException(HttpStatus.CONFLICT, 1006, message); }
  public static AppException invalid(String message) { return new AppException(HttpStatus.UNPROCESSABLE_ENTITY, 1007, message); }
}
