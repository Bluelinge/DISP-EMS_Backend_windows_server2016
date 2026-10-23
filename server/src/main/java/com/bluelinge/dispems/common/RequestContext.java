package com.bluelinge.dispems.common;

import java.util.UUID;

public final class RequestContext {
  private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<String>();
  private RequestContext() { }
  public static void begin() { REQUEST_ID.set(UUID.randomUUID().toString().replace("-", "")); }
  public static String requestId() {
    String value = REQUEST_ID.get();
    return value == null ? "" : value;
  }
  public static void clear() { REQUEST_ID.remove(); }
}
