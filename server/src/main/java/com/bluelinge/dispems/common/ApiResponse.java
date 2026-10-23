package com.bluelinge.dispems.common;

public class ApiResponse<T> {
  private final int code;
  private final String message;
  private final T data;
  private final String requestId;

  private ApiResponse(int code, String message, T data, String requestId) {
    this.code = code;
    this.message = message;
    this.data = data;
    this.requestId = requestId;
  }
  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<T>(0, "成功", data, RequestContext.requestId());
  }
  public static <T> ApiResponse<T> error(int code, String message) {
    return new ApiResponse<T>(code, message, null, RequestContext.requestId());
  }
  public int getCode() { return code; }
  public String getMessage() { return message; }
  public T getData() { return data; }
  public String getRequestId() { return requestId; }
}
