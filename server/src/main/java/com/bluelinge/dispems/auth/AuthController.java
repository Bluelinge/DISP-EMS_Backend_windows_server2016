package com.bluelinge.dispems.auth;

import com.bluelinge.dispems.common.ApiResponse;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService service;
  public AuthController(AuthService service) { this.service = service; }
  @PostMapping("/login")
  public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.ok(service.login(request.no, request.password, request.portCode, request.workstationId));
  }
  @GetMapping("/me")
  public ApiResponse<Map<String, Object>> me() { return ApiResponse.ok(service.me(AuthPrincipal.required())); }
  @PostMapping("/port")
  public ApiResponse<Map<String, Object>> port(@Valid @RequestBody PortRequest request) {
    return ApiResponse.ok(service.switchPort(AuthPrincipal.required(), request.portCode, request.workstationId));
  }
  @PostMapping("/logout")
  public ApiResponse<Void> logout(javax.servlet.http.HttpServletRequest request) {
    String token = request.getHeader("Authorization").substring(7);
    service.logout(AuthPrincipal.required(), token); return ApiResponse.ok(null);
  }
  @PostMapping("/change-password")
  public ApiResponse<Void> password(@Valid @RequestBody PasswordRequest request) {
    service.changePassword(AuthPrincipal.required(), request.currentPassword, request.newPassword);
    return ApiResponse.ok(null);
  }
  public static class LoginRequest {
    @NotBlank public String no; @NotBlank public String password; @NotNull public Integer portCode; public Long workstationId;
  }
  public static class PortRequest { @NotNull public Integer portCode; public Long workstationId; }
  public static class PasswordRequest { @NotBlank public String currentPassword; @NotBlank public String newPassword; }
}
