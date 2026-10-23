package com.bluelinge.dispems.auth;

import com.bluelinge.dispems.common.AppException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthPrincipal {
  private AuthPrincipal() { }
  public static CurrentUser required() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser)) {
      throw AppException.unauthorized();
    }
    return (CurrentUser) authentication.getPrincipal();
  }
  public static void requireAdmin() {
    if (!required().isAdmin()) throw AppException.forbidden("需要管理员权限");
  }
  public static void requireSuperAdmin() {
    if (!required().isSuperAdmin()) throw AppException.forbidden("需要超级管理员权限");
  }
}
