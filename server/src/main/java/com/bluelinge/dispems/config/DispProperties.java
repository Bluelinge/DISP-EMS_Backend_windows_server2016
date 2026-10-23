package com.bluelinge.dispems.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "disp")
public class DispProperties {
  private Security security = new Security();
  private Storage storage = new Storage();
  private Bootstrap bootstrap = new Bootstrap();
  private Cors cors = new Cors();
  public Security getSecurity() { return security; }
  public void setSecurity(Security security) { this.security = security; }
  public Storage getStorage() { return storage; }
  public void setStorage(Storage storage) { this.storage = storage; }
  public Bootstrap getBootstrap() { return bootstrap; }
  public void setBootstrap(Bootstrap bootstrap) { this.bootstrap = bootstrap; }
  public Cors getCors() { return cors; }
  public void setCors(Cors cors) { this.cors = cors; }
  public static class Security {
    private int sessionHours = 8;
    public int getSessionHours() { return sessionHours; }
    public void setSessionHours(int sessionHours) { this.sessionHours = sessionHours; }
  }
  public static class Storage {
    private String endpoint;
    private String publicEndpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private int urlExpiryMinutes = 15;
    private String ffprobePath = "ffprobe";
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getPublicEndpoint() { return publicEndpoint; }
    public void setPublicEndpoint(String publicEndpoint) { this.publicEndpoint = publicEndpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public int getUrlExpiryMinutes() { return urlExpiryMinutes; }
    public void setUrlExpiryMinutes(int urlExpiryMinutes) { this.urlExpiryMinutes = urlExpiryMinutes; }
    public String getFfprobePath() { return ffprobePath; }
    public void setFfprobePath(String ffprobePath) { this.ffprobePath = ffprobePath; }
  }
  public static class Bootstrap {
    private String adminNo;
    private String adminName;
    private String adminPassword;
    public String getAdminNo() { return adminNo; }
    public void setAdminNo(String adminNo) { this.adminNo = adminNo; }
    public String getAdminName() { return adminName; }
    public void setAdminName(String adminName) { this.adminName = adminName; }
    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
  }
  public static class Cors {
    private String allowedOrigins;
    public String getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
  }
}
