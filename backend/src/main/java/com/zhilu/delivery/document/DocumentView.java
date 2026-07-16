package com.zhilu.delivery.document;

import java.time.Instant;

public final class DocumentView {
  private final long linkId;
  private final String title;
  private final String markdown;
  private final long revision;
  private final Instant updatedAt;
  private final String syncStatus;
  private final String lastError;
  private final String outlineUrl;

  public DocumentView(
      long linkId, String title, String markdown, long revision, Instant updatedAt,
      String syncStatus, String lastError, String outlineUrl) {
    this.linkId = linkId;
    this.title = title;
    this.markdown = markdown;
    this.revision = revision;
    this.updatedAt = updatedAt;
    this.syncStatus = syncStatus;
    this.lastError = lastError;
    this.outlineUrl = outlineUrl;
  }

  public long getLinkId() {
    return linkId;
  }

  public String getTitle() {
    return title;
  }

  public String getMarkdown() {
    return markdown;
  }

  public long getRevision() {
    return revision;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public String getSyncStatus() {
    return syncStatus;
  }

  public String getLastError() {
    return lastError;
  }

  public String getOutlineUrl() {
    return outlineUrl;
  }
}
