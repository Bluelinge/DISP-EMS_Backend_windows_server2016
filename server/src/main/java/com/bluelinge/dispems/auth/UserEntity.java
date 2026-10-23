package com.bluelinge.dispems.auth;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_user")
public class UserEntity {
  @TableId private Long id;
  private String no;
  private String name;
  private String passwordHash;
  private Integer roleCode;
  private String department;
  private String phone;
  private Integer statusCode;
  private Integer isDeleted;
  private Integer rowVersion;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  public Long getId() { return id; } public void setId(Long v) { id = v; }
  public String getNo() { return no; } public void setNo(String v) { no = v; }
  public String getName() { return name; } public void setName(String v) { name = v; }
  public String getPasswordHash() { return passwordHash; } public void setPasswordHash(String v) { passwordHash = v; }
  public Integer getRoleCode() { return roleCode; } public void setRoleCode(Integer v) { roleCode = v; }
  public String getDepartment() { return department; } public void setDepartment(String v) { department = v; }
  public String getPhone() { return phone; } public void setPhone(String v) { phone = v; }
  public Integer getStatusCode() { return statusCode; } public void setStatusCode(Integer v) { statusCode = v; }
  public Integer getIsDeleted() { return isDeleted; } public void setIsDeleted(Integer v) { isDeleted = v; }
  public Integer getRowVersion() { return rowVersion; } public void setRowVersion(Integer v) { rowVersion = v; }
  public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
  public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}
