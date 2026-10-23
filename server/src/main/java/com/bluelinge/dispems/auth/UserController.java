package com.bluelinge.dispems.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluelinge.dispems.auth.mapper.UserMapper;
import com.bluelinge.dispems.common.ApiResponse;
import com.bluelinge.dispems.common.AppException;
import com.bluelinge.dispems.common.TimeProvider;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
  private final UserMapper users;
  private final AuthService auth;
  public UserController(UserMapper users, AuthService auth) { this.users = users; this.auth = auth; }
  @GetMapping
  public ApiResponse<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String keyword, @RequestParam(required = false) Integer roleCode, @RequestParam(required = false) Integer statusCode) {
    AuthPrincipal.requireSuperAdmin();
    LambdaQueryWrapper<UserEntity> query = new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getIsDeleted, 0);
    if (keyword != null && !keyword.trim().isEmpty()) query.and(q -> q.like(UserEntity::getNo, keyword.trim()).or().like(UserEntity::getName, keyword.trim()));
    if (roleCode != null) query.eq(UserEntity::getRoleCode, roleCode);
    if (statusCode != null) query.eq(UserEntity::getStatusCode, statusCode);
    Page<UserEntity> result = users.selectPage(new Page<UserEntity>(Math.max(1, page), Math.min(100, Math.max(1, pageSize))), query.orderByAsc(UserEntity::getNo));
    List<Map<String, Object>> items = new ArrayList<Map<String, Object>>(); for (UserEntity user : result.getRecords()) items.add(map(user));
    Map<String, Object> data = new LinkedHashMap<String, Object>(); data.put("items", items); data.put("page", page); data.put("pageSize", pageSize); data.put("total", result.getTotal()); return ApiResponse.ok(data);
  }
  @PostMapping
  @Transactional
  public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateRequest request) {
    AuthPrincipal.requireSuperAdmin();
    if (request.password.length() < 5) throw AppException.badRequest("密码至少 5 位");
    Long existing = users.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getNo, request.no.trim()));
    if (existing.longValue() > 0) throw AppException.conflict("工号已存在");
    LocalDateTime now = TimeProvider.now();
    UserEntity user = new UserEntity(); user.setNo(request.no.trim()); user.setName(request.name.trim()); user.setPasswordHash(auth.hashPassword(request.password));
    user.setRoleCode(request.roleCode); user.setDepartment(blank(request.department)); user.setPhone(blank(request.phone)); user.setStatusCode(request.statusCode);
    user.setIsDeleted(0); user.setRowVersion(1); user.setCreatedAt(now); user.setUpdatedAt(now); users.insert(user); return ApiResponse.ok(map(user));
  }
  @PatchMapping("/{id}")
  @Transactional
  public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
    CurrentUser current = AuthPrincipal.required(); AuthPrincipal.requireSuperAdmin();
    UserEntity user = required(id); checkVersion(user, request.expectedVersion);
    if (id.equals(current.getId()) && request.statusCode.intValue() != 1) throw AppException.invalid("不能停用当前登录账号");
    user.setName(request.name.trim()); user.setRoleCode(request.roleCode); user.setDepartment(blank(request.department)); user.setPhone(blank(request.phone)); user.setStatusCode(request.statusCode);
    user.setRowVersion(user.getRowVersion() + 1); user.setUpdatedAt(TimeProvider.now()); users.updateById(user);
    auth.revokeAllSessions(id); return ApiResponse.ok(map(user));
  }
  @PostMapping("/{id}/reset-password")
  @Transactional
  public ApiResponse<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
    AuthPrincipal.requireSuperAdmin();
    if (request.newPassword.length() < 5) throw AppException.badRequest("密码至少 5 位");
    UserEntity user = required(id); checkVersion(user, request.expectedVersion);
    user.setPasswordHash(auth.hashPassword(request.newPassword)); user.setRowVersion(user.getRowVersion() + 1); user.setUpdatedAt(TimeProvider.now()); users.updateById(user);
    auth.revokeAllSessions(id); return ApiResponse.ok(null);
  }
  @DeleteMapping("/{id}")
  @Transactional
  public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam(name = "expectedVersion") Integer expectedVersion) {
    CurrentUser current = AuthPrincipal.required(); AuthPrincipal.requireSuperAdmin();
    if (id.equals(current.getId())) throw AppException.invalid("不能删除当前登录账号");
    UserEntity user = required(id); checkVersion(user, expectedVersion);
    if (user.getRoleCode().intValue() == 2) {
      Long activeAdmins = users.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getIsDeleted, 0).eq(UserEntity::getStatusCode, 1).eq(UserEntity::getRoleCode, 2));
      if (activeAdmins.longValue() <= 1) throw AppException.invalid("不能删除最后一个在岗超级管理员");
    }
    user.setIsDeleted(1); user.setStatusCode(0); user.setRowVersion(user.getRowVersion() + 1); user.setUpdatedAt(TimeProvider.now()); users.updateById(user); auth.revokeAllSessions(id); return ApiResponse.ok(null);
  }
  private UserEntity required(Long id) { UserEntity user = users.selectById(id); if (user == null || user.getIsDeleted().intValue() == 1) throw AppException.notFound("人员不存在"); return user; }
  private void checkVersion(UserEntity user, Integer expected) { if (expected == null || user.getRowVersion().intValue() != expected.intValue()) throw AppException.conflict("人员已更新，请刷新后重试"); }
  private String blank(String value) { return value == null ? "" : value.trim(); }
  private Map<String, Object> map(UserEntity user) {
    Map<String, Object> item = new LinkedHashMap<String, Object>(); item.put("id", String.valueOf(user.getId())); item.put("no", user.getNo()); item.put("name", user.getName());
    item.put("roleCode", user.getRoleCode()); item.put("department", user.getDepartment()); item.put("phone", user.getPhone()); item.put("statusCode", user.getStatusCode()); item.put("rowVersion", user.getRowVersion()); return item;
  }
  public static class CreateRequest { @NotBlank public String no; @NotBlank public String name; @NotBlank public String password; @NotNull public Integer roleCode; @NotNull public Integer statusCode; public String department; public String phone; }
  public static class UpdateRequest { @NotBlank public String name; @NotNull public Integer roleCode; @NotNull public Integer statusCode; @NotNull public Integer expectedVersion; public String department; public String phone; }
  public static class ResetPasswordRequest { @NotBlank public String newPassword; @NotNull public Integer expectedVersion; }
}
