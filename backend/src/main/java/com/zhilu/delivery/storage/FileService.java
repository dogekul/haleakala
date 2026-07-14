package com.zhilu.delivery.storage;

import com.zhilu.delivery.iam.service.CurrentUser;
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

  FileObjectView store(
      InputStream content,
      String fileName,
      String mimeType,
      long size,
      CurrentUser user);

  URI signedDownload(long fileId, Duration ttl, CurrentUser user);

  FileObjectView addVersion(
      long fileId, InputStream content, String mimeType, long size, CurrentUser user);
}
