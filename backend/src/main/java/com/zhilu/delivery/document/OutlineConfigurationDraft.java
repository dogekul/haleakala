package com.zhilu.delivery.document;

public final class OutlineConfigurationDraft {
  private final OutlineConnection connection;
  private final String collectionReference;
  private final boolean tokenChanged;

  public OutlineConfigurationDraft(
      OutlineConnection connection, String collectionReference, boolean tokenChanged) {
    this.connection = connection;
    this.collectionReference = collectionReference;
    this.tokenChanged = tokenChanged;
  }

  public OutlineConnection getConnection() {
    return connection;
  }

  public String getCollectionReference() {
    return collectionReference;
  }

  public boolean isTokenChanged() {
    return tokenChanged;
  }
}
