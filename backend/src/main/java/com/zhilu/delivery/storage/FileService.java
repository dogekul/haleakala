package com.zhilu.delivery.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

public interface FileService {
  FileObjectView store(
      InputStream content,
      String fileName,
      String mimeType,
      long size,
      long organizationId,
      long actorUserId);

  URI signedDownload(long fileId, Duration ttl);

  FileObjectView addVersion(
      long fileId, InputStream content, String mimeType, long size, long actorUserId);
}
