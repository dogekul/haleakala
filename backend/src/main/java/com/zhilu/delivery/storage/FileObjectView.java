package com.zhilu.delivery.storage;

public final class FileObjectView {
  private final long id;
  private final long organizationId;
  private final String objectKey;
  private final String originalName;
  private final String mimeType;
  private final long sizeBytes;
  private final String checksumSha256;
  private final int fileVersion;

  public FileObjectView(
      long id,
      long organizationId,
      String objectKey,
      String originalName,
      String mimeType,
      long sizeBytes,
      String checksumSha256,
      int fileVersion) {
    this.id = id;
    this.organizationId = organizationId;
    this.objectKey = objectKey;
    this.originalName = originalName;
    this.mimeType = mimeType;
    this.sizeBytes = sizeBytes;
    this.checksumSha256 = checksumSha256;
    this.fileVersion = fileVersion;
  }

  public long getId() { return id; }
  public long getOrganizationId() { return organizationId; }
  public String getObjectKey() { return objectKey; }
  public String getOriginalName() { return originalName; }
  public String getMimeType() { return mimeType; }
  public long getSizeBytes() { return sizeBytes; }
  public String getChecksumSha256() { return checksumSha256; }
  public int getFileVersion() { return fileVersion; }
}
