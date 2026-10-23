package com.bluelinge.dispems.auth;

public class CurrentUser {
  private final Long id;
  private final String no;
  private final String name;
  private final int roleCode;
  private final int portCode;
  private final Long workstationId;
  public CurrentUser(Long id, String no, String name, int roleCode, int portCode, Long workstationId) {
    this.id = id; this.no = no; this.name = name; this.roleCode = roleCode; this.portCode = portCode; this.workstationId = workstationId;
  }
  public Long getId() { return id; }
  public String getNo() { return no; }
  public String getName() { return name; }
  public int getRoleCode() { return roleCode; }
  public int getPortCode() { return portCode; }
  public Long getWorkstationId() { return workstationId; }
  public boolean isAdmin() { return roleCode >= 1; }
  public boolean isSuperAdmin() { return roleCode >= 2; }
}
