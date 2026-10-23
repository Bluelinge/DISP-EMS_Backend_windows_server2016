package com.bluelinge.dispems.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluelinge.dispems.auth.AuthService;
import com.bluelinge.dispems.auth.UserEntity;
import com.bluelinge.dispems.auth.mapper.UserMapper;
import com.bluelinge.dispems.common.TimeProvider;
import java.time.LocalDateTime;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class BootstrapData implements ApplicationRunner {
  private final UserMapper users;
  private final AuthService auth;
  private final JdbcTemplate jdbc;
  private final DispProperties properties;
  public BootstrapData(UserMapper users, AuthService auth, JdbcTemplate jdbc, DispProperties properties) {
    this.users = users; this.auth = auth; this.jdbc = jdbc; this.properties = properties;
  }
  @Override public void run(ApplicationArguments args) {
    LocalDateTime now = TimeProvider.now();
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM workstation", Integer.class);
    if (count != null && count.intValue() == 0) {
      for (int i = 1; i <= 7; i++) {
        jdbc.update("INSERT INTO workstation(station_no,name,capture_mode,is_default,is_deleted,row_version,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
            i, "工位" + i, i == 1 ? 0 : 1, i == 1 ? 1 : 0, 0, 1, now, now);
      }
    }
    Integer categoryCount = jdbc.queryForObject("SELECT COUNT(*) FROM video_category", Integer.class);
    if (categoryCount != null && categoryCount.intValue() == 0) {
      jdbc.update("INSERT INTO video_category(name,parent_id,level_no,sort_order,is_deleted,row_version,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
          "通用", null, 0, 1, 0, 1, now, now);
    }
    Integer templateCount = jdbc.queryForObject("SELECT COUNT(*) FROM projection_template", Integer.class);
    if (templateCount != null && templateCount.intValue() == 0) {
      String elements = "[{\"id\":\"E-01\",\"type\":\"text\",\"text\":\"{{stepNo}}\",\"bindField\":\"stepNo\",\"x\":460,\"y\":286,\"fontSize\":80,\"color\":\"#59C2DA\",\"zIndex\":2}]";
      jdbc.update("INSERT INTO projection_template(name,description,is_default,width_px,height_px,elements_json,is_deleted,row_version,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
          "默认工序引导", "系统默认模板", 1, 1920, 1080, elements, 0, 1, now, now);
    }
    if (!StringUtils.hasText(properties.getBootstrap().getAdminPassword())) return;
    UserEntity existing = users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getNo, properties.getBootstrap().getAdminNo()));
    if (existing != null) return;
    UserEntity user = new UserEntity();
    user.setNo(properties.getBootstrap().getAdminNo()); user.setName(properties.getBootstrap().getAdminName());
    user.setPasswordHash(auth.hashPassword(properties.getBootstrap().getAdminPassword()));
    user.setRoleCode(2); user.setDepartment("系统管理"); user.setPhone(""); user.setStatusCode(1); user.setIsDeleted(0);
    user.setRowVersion(1); user.setCreatedAt(now); user.setUpdatedAt(now);
    users.insert(user);
  }
}
