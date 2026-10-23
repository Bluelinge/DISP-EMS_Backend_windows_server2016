package com.bluelinge.dispems.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluelinge.dispems.common.AppException;
import com.bluelinge.dispems.common.TimeProvider;
import com.bluelinge.dispems.auth.mapper.AuthSessionMapper;
import com.bluelinge.dispems.auth.mapper.UserMapper;
import com.bluelinge.dispems.config.DispProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private static final String CACHE_PREFIX = "disp:session:";
  private final UserMapper users;
  private final AuthSessionMapper sessions;
  private final StringRedisTemplate redis;
  private final DispProperties properties;
  private final SecureRandom random = new SecureRandom();
  private final Argon2PasswordEncoder passwords = new Argon2PasswordEncoder(16, 32, 1, 4096, 3);

  public AuthService(UserMapper users, AuthSessionMapper sessions, StringRedisTemplate redis, DispProperties properties) {
    this.users = users; this.sessions = sessions; this.redis = redis; this.properties = properties;
  }
  public String hashPassword(String password) { return passwords.encode(password); }
  @Transactional
  public Map<String, Object> login(String no, String password, int portCode, Long workstationId) {
    UserEntity user = users.selectOne(new LambdaQueryWrapper<UserEntity>()
        .eq(UserEntity::getNo, no.trim()).eq(UserEntity::getIsDeleted, 0));
    if (user == null || user.getStatusCode() == null || user.getStatusCode().intValue() != 1
        || !passwords.matches(password, user.getPasswordHash())) {
      throw AppException.unauthorized();
    }
    if (portCode == 0 && workstationId == null) throw AppException.badRequest("操作端登录必须选择工位");
    if (portCode == 1 && user.getRoleCode().intValue() == 0) throw AppException.forbidden("操作员不能进入管理端");
    return createSession(user, portCode, workstationId);
  }
  @Transactional
  public Map<String, Object> switchPort(CurrentUser current, int portCode, Long workstationId) {
    if (portCode == 0 && workstationId == null) throw AppException.badRequest("操作端必须选择工位");
    if (portCode == 1 && !current.isAdmin()) throw AppException.forbidden("没有管理端权限");
    UserEntity user = users.selectById(current.getId());
    revokeUserSessions(user.getId());
    return createSession(user, portCode, workstationId);
  }
  public CurrentUser resolve(String token) {
    String hash = sha256(token);
    String cachedId = redis.opsForValue().get(CACHE_PREFIX + hash);
    AuthSessionEntity session = null;
    if (cachedId != null) session = sessions.selectById(Long.valueOf(cachedId));
    if (session == null) {
      session = sessions.selectOne(new LambdaQueryWrapper<AuthSessionEntity>().eq(AuthSessionEntity::getTokenHash, hash));
      if (session != null && isActive(session)) cacheSession(session);
    }
    if (session == null || !isActive(session)) throw AppException.unauthorized();
    UserEntity user = users.selectById(session.getUserId());
    if (user == null || user.getIsDeleted().intValue() == 1 || user.getStatusCode().intValue() != 1) {
      revokeSession(session);
      throw AppException.unauthorized();
    }
    return new CurrentUser(user.getId(), user.getNo(), user.getName(), user.getRoleCode(), session.getPortCode(), session.getWorkstationId());
  }
  @Transactional
  public void logout(CurrentUser current, String token) {
    AuthSessionEntity session = sessions.selectOne(new LambdaQueryWrapper<AuthSessionEntity>().eq(AuthSessionEntity::getTokenHash, sha256(token)));
    if (session != null && session.getUserId().equals(current.getId())) revokeSession(session);
  }
  @Transactional
  public void changePassword(CurrentUser current, String oldPassword, String newPassword) {
    if (newPassword == null || newPassword.length() < 5) throw AppException.badRequest("新密码至少 5 位");
    UserEntity user = users.selectById(current.getId());
    if (!passwords.matches(oldPassword, user.getPasswordHash())) throw AppException.badRequest("原密码不正确");
    if (passwords.matches(newPassword, user.getPasswordHash())) throw AppException.badRequest("新密码不能与原密码相同");
    user.setPasswordHash(passwords.encode(newPassword));
    user.setUpdatedAt(TimeProvider.now());
    user.setRowVersion(user.getRowVersion() + 1);
    users.updateById(user);
    revokeUserSessions(current.getId());
  }
  public Map<String, Object> me(CurrentUser user) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("user", userMap(user));
    result.put("portCode", user.getPortCode());
    result.put("workstationId", user.getWorkstationId() == null ? null : String.valueOf(user.getWorkstationId()));
    result.put("permissions", permissions(user));
    return result;
  }
  @Transactional
  public void revokeAllSessions(Long userId) { revokeUserSessions(userId); }
  public Map<String, Object> userMap(CurrentUser user) {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("id", String.valueOf(user.getId())); value.put("no", user.getNo()); value.put("name", user.getName());
    value.put("roleCode", user.getRoleCode()); return value;
  }
  private Map<String, Object> createSession(UserEntity user, int portCode, Long workstationId) {
    LocalDateTime now = TimeProvider.now();
    String token = newToken();
    AuthSessionEntity session = new AuthSessionEntity();
    session.setUserId(user.getId()); session.setTokenHash(sha256(token)); session.setPortCode(portCode);
    session.setWorkstationId(workstationId); session.setSessionState(1);
    session.setCreatedAt(now); session.setUpdatedAt(now); session.setExpiresAt(now.plusHours(properties.getSecurity().getSessionHours()));
    sessions.insert(session); cacheSession(session);
    CurrentUser current = new CurrentUser(user.getId(), user.getNo(), user.getName(), user.getRoleCode(), portCode, workstationId);
    Map<String, Object> result = me(current);
    result.put("accessToken", token); result.put("expiresAt", session.getExpiresAt().toString() + "Z");
    return result;
  }
  private void revokeUserSessions(Long userId) {
    for (AuthSessionEntity item : sessions.selectList(new LambdaQueryWrapper<AuthSessionEntity>()
        .eq(AuthSessionEntity::getUserId, userId).eq(AuthSessionEntity::getSessionState, 1))) revokeSession(item);
  }
  private void revokeSession(AuthSessionEntity session) {
    session.setSessionState(0); session.setRevokedAt(TimeProvider.now()); session.setUpdatedAt(TimeProvider.now());
    sessions.updateById(session); redis.delete(CACHE_PREFIX + session.getTokenHash());
  }
  private boolean isActive(AuthSessionEntity session) {
    return session.getSessionState() != null && session.getSessionState().intValue() == 1
        && session.getExpiresAt().isAfter(TimeProvider.now());
  }
  private void cacheSession(AuthSessionEntity session) {
    long seconds = Math.max(1, java.time.Duration.between(TimeProvider.now(), session.getExpiresAt()).getSeconds());
    redis.opsForValue().set(CACHE_PREFIX + session.getTokenHash(), String.valueOf(session.getId()), seconds, TimeUnit.SECONDS);
  }
  private String newToken() {
    byte[] bytes = new byte[32]; random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
  public static String sha256(String raw) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
      StringBuilder value = new StringBuilder(64);
      for (byte b : digest) value.append(String.format("%02x", b));
      return value.toString();
    } catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
  }
  private Map<String, Integer> permissions(CurrentUser user) {
    Map<String, Integer> value = new LinkedHashMap<String, Integer>();
    value.put("canOperate", user.getPortCode() == 0 ? 1 : 0);
    value.put("canManageProcesses", user.isAdmin() ? 1 : 0);
    value.put("canManageUsers", user.isSuperAdmin() ? 1 : 0);
    return value;
  }
}
