package com.zhilu.delivery.document;

import java.time.Instant;

public final class OutlineDocument {
  private final String id;
  private final String collectionId;
  private final String parentDocumentId;
  private final String title;
  private final String text;
  private final String url;
  private final String urlId;
  private final long revision;
  private final Instant updatedAt;

  public OutlineDocument(
      String id, String collectionId, String parentDocumentId, String title, String text,
      String url, String urlId, long revision, Instant updatedAt) {
    this.id = id;
    this.collectionId = collectionId;
    this.parentDocumentId = parentDocumentId;
    this.title = title;
    this.text = text;
    this.url = url;
    this.urlId = urlId;
    this.revision = revision;
    this.updatedAt = updatedAt;
  }

  public String getId() {
    return id;
  }

  public String getCollectionId() {
    return collectionId;
  }

  public String getParentDocumentId() {
    return parentDocumentId;
  }

  public String getTitle() {
    return title;
  }

  public String getText() {
    return text;
  }

  public String getUrl() {
    return url;
  }

  public String getUrlId() {
    return urlId;
  }

  public long getRevision() {
    return revision;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
