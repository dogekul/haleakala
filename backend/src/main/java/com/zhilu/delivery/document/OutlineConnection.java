package com.zhilu.delivery.document;

public final class OutlineConnection {
  private final long organizationId;
  private final String baseUrl;
  private final String publicBaseUrl;
  private final String apiToken;
  private final String collectionId;
  private final String collectionName;
  private final String source;

  public OutlineConnection(
      long organizationId, String baseUrl, String publicBaseUrl, String apiToken,
      String collectionId, String collectionName, String source) {
    this.organizationId = organizationId;
    this.baseUrl = baseUrl;
    this.publicBaseUrl = publicBaseUrl;
    this.apiToken = apiToken;
    this.collectionId = collectionId;
    this.collectionName = collectionName;
    this.source = source;
  }

  public long getOrganizationId() {
    return organizationId;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }

  public String getApiToken() {
    return apiToken;
  }

  public String getCollectionId() {
    return collectionId;
  }

  public String getCollectionName() {
    return collectionName;
  }

  public String getSource() {
    return source;
  }

  public boolean isConfigured() {
    return !blank(baseUrl) && !blank(apiToken) && !blank(collectionId);
  }

  private boolean blank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
