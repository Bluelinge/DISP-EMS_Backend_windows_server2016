package com.bluelinge.dispems;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableAsync
@EnableScheduling
@MapperScan(basePackages = {
    "com.bluelinge.dispems.auth.mapper",
    "com.bluelinge.dispems.file.mapper",
    "com.bluelinge.dispems.media.mapper"
})
public class DispEmsApplication {
  public static void main(String[] args) {
    SpringApplication.run(DispEmsApplication.class, args);
  }
}
