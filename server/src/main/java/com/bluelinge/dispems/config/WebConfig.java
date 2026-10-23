package com.bluelinge.dispems.config;

import com.bluelinge.dispems.common.RequestContext;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {
  @Bean
  public Filter requestIdFilter() {
    return new Filter() {
      @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
          throws IOException, ServletException {
        RequestContext.begin();
        try {
          ((HttpServletResponse) response).setHeader("X-Request-Id", RequestContext.requestId());
          chain.doFilter(request, response);
        } finally {
          RequestContext.clear();
        }
      }
    };
  }
  @Bean
  public WebMvcConfigurer corsConfigurer(final DispProperties properties) {
    return new WebMvcConfigurer() {
      @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(properties.getCors().getAllowedOrigins().split(","))
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("X-Request-Id")
            .allowCredentials(false);
      }
    };
  }
}
