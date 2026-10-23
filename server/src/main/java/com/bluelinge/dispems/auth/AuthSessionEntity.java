package com.bluelinge.dispems.auth;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("auth_session")
public class AuthSessionEntity {
  @TableId private Long id;
  private Long userId;
  private String tokenHash;
  private Integer portCode;
  private Long workstationId;
  private Integer sessionState;
  private LocalDateTime expiresAt;
  private LocalDateTime revokedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  public Long getId() { return id; } public void setId(Long v) { id = v; }
  public Long getUserId() { return userId; } public void setUserId(Long v) { userId = v; }
  public String getTokenHash() { return tokenHash; } public void setTokenHash(String v) { tokenHash = v; }
  public Integer getPortCode() { return portCode; } public void setPortCode(Integer v) { portCode = v; }
  public Long getWorkstationId() { return workstationId; } public void setWorkstationId(Long v) { workstationId = v; }
  public Integer getSessionState() { return sessionState; } public void setSessionState(Integer v) { sessionState = v; }
  public LocalDateTime getExpiresAt() { return expiresAt; } public void setExpiresAt(LocalDateTime v) { expiresAt = v; }
  public LocalDateTime getRevokedAt() { return revokedAt; } public void setRevokedAt(LocalDateTime v) { revokedAt = v; }
  public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
  public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}
