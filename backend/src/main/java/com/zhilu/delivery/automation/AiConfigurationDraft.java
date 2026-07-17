package com.zhilu.delivery.automation;

public final class AiConfigurationDraft {
  private final AiConnection connection;
  private final boolean apiKeyChanged;

  public AiConfigurationDraft(AiConnection connection, boolean apiKeyChanged) {
    this.connection = connection;
    this.apiKeyChanged = apiKeyChanged;
  }

  public AiConnection getConnection() {
    return connection;
  }

  public boolean isApiKeyChanged() {
    return apiKeyChanged;
  }
}
