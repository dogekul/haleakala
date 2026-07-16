package com.zhilu.delivery.document;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "delivery.outline")
public class OutlineProperties {
  private String baseUrl = "http://localhost:3000";
  private String publicBaseUrl = "";
  private String apiToken = "";
  private String collectionId = "";
  private Duration connectTimeout = Duration.ofSeconds(3);
  private Duration readTimeout = Duration.ofSeconds(10);
  private int maxAttempts = 5;
  private Duration initialBackoff = Duration.ofSeconds(30);
  private Duration staleAfter = Duration.ofMinutes(5);

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }

  public void setPublicBaseUrl(String publicBaseUrl) {
    this.publicBaseUrl = publicBaseUrl;
  }

  public String getApiToken() {
    return apiToken;
  }

  public void setApiToken(String apiToken) {
    this.apiToken = apiToken;
  }

  public String getCollectionId() {
    return collectionId;
  }

  public void setCollectionId(String collectionId) {
    this.collectionId = collectionId;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(Duration readTimeout) {
    this.readTimeout = readTimeout;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public Duration getInitialBackoff() {
    return initialBackoff;
  }

  public void setInitialBackoff(Duration initialBackoff) {
    this.initialBackoff = initialBackoff;
  }

  public Duration getStaleAfter() {
    return staleAfter;
  }

  public void setStaleAfter(Duration staleAfter) {
    this.staleAfter = staleAfter;
  }
}
