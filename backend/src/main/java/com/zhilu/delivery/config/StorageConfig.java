package com.zhilu.delivery.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class StorageConfig {
  @Bean
  @Primary
  public MinioClient minioClient(
      @Value("${delivery.storage.endpoint}") String endpoint,
      @Value("${delivery.storage.access-key}") String accessKey,
      @Value("${delivery.storage.secret-key}") String secretKey) {
    return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
  }

  @Bean("publicMinioClient")
  public MinioClient publicMinioClient(
      @Value("${delivery.storage.public-endpoint:${delivery.storage.endpoint}}") String endpoint,
      @Value("${delivery.storage.access-key}") String accessKey,
      @Value("${delivery.storage.secret-key}") String secretKey) {
    return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
  }
}
