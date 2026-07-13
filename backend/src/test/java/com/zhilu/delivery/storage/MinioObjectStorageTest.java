package com.zhilu.delivery.storage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class MinioObjectStorageTest {
  @Test
  void createsConfiguredBucketWhenItDoesNotExist() throws Exception {
    MinioClient client = mock(MinioClient.class);
    when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

    MinioObjectStorage storage = new MinioObjectStorage(client, "delivery");
    storage.put("test.txt", new ByteArrayInputStream(new byte[] {1}), 1, "text/plain");

    verify(client).makeBucket(any(MakeBucketArgs.class));
  }
}
