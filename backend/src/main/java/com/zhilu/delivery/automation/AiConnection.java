package com.zhilu.delivery.automation;

public final class AiConnection {
  private final long organizationId;
  private final String baseUrl;
  private final String model;
  private final String apiKey;
  private final String source;

  public AiConnection(long organizationId, String baseUrl, String model, String apiKey,
      String source) {
    this.organizationId = organizationId;
    this.baseUrl = baseUrl;
    this.model = model;
    this.apiKey = apiKey;
    this.source = source;
  }

  public long getOrganizationId() {
    return organizationId;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getModel() {
    return model;
  }

  public String getApiKey() {
    return apiKey;
  }

  public String getSource() {
    return source;
  }

  public boolean isConfigured() {
    return !blank(baseUrl) && !blank(model) && !blank(apiKey);
  }

  private boolean blank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
