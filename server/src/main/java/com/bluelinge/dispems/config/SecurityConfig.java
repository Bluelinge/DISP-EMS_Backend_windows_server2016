package com.bluelinge.dispems.config;

import com.bluelinge.dispems.auth.AuthService;
import com.bluelinge.dispems.auth.CurrentUser;
import com.bluelinge.dispems.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Collections;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
  private final AuthService auth;
  private final ObjectMapper objectMapper;
  public SecurityConfig(AuthService auth, ObjectMapper objectMapper) { this.auth = auth; this.objectMapper = objectMapper; }
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf().disable().formLogin().disable().httpBasic().disable()
        .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and().authorizeRequests()
        .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .antMatchers("/api/v1/auth/login", "/api/v1/workstations/options", "/actuator/health").permitAll()
        .anyRequest().authenticated()
        .and().exceptionHandling().authenticationEntryPoint((request, response, exception) -> writeUnauthorized(response))
        .and().addFilterBefore(new TokenFilter(), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
  private void writeUnauthorized(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), ApiResponse.<Void>error(1002, "登录已过期，请重新登录"));
  }
  private class TokenFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
      return request.getRequestURI().endsWith("/api/v1/auth/login") || request.getRequestURI().endsWith("/api/v1/workstations/options");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      String value = request.getHeader("Authorization");
      if (value != null && value.startsWith("Bearer ")) {
        try {
          CurrentUser user = auth.resolve(value.substring(7).trim());
          SecurityContextHolder.getContext().setAuthentication(
              new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
        } catch (RuntimeException ignored) {
          SecurityContextHolder.clearContext();
        }
      }
      chain.doFilter(request, response);
    }
  }
}
