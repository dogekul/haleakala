package com.zhilu.delivery.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

public interface ObjectStorage {
  void put(String objectKey, InputStream content, long size, String mimeType);
  URI signedDownload(String objectKey, Duration ttl);

  void delete(String objectKey);
}
