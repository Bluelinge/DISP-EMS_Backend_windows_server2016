package com.bluelinge.dispems.common;

import java.time.LocalDateTime;

public final class TimeProvider {
  private TimeProvider() { }
  public static LocalDateTime now() { return LocalDateTime.now(java.time.Clock.systemUTC()); }
}
