package com.bluelinge.dispems.file;

import com.bluelinge.dispems.config.DispProperties;
import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {
  @Bean("minioClient")
  public MinioClient minioClient(DispProperties properties) {
    return MinioClient.builder()
        .endpoint(properties.getStorage().getEndpoint())
        .credentials(properties.getStorage().getAccessKey(), properties.getStorage().getSecretKey())
        .build();
  }

  @Bean("publicMinioClient")
  public MinioClient publicMinioClient(DispProperties properties) {
    return MinioClient.builder()
        .endpoint(properties.getStorage().getPublicEndpoint())
        .region("us-east-1")
        .credentials(properties.getStorage().getAccessKey(), properties.getStorage().getSecretKey())
        .build();
  }
}
