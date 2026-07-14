package com.zhilu.delivery.storage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class MinioObjectStorageTest {
  @Test
  void createsConfiguredBucketWhenItDoesNotExist() throws Exception {
    MinioClient client = mock(MinioClient.class);
    MinioClient publicClient = mock(MinioClient.class);
    when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

    MinioObjectStorage storage = new MinioObjectStorage(client, publicClient, "delivery");
    storage.put("test.txt", new ByteArrayInputStream(new byte[] {1}), 1, "text/plain");

    verify(client).makeBucket(any(MakeBucketArgs.class));
  }

  @Test
  void signsBrowserDownloadWithPublicClientInsteadOfInternalClient() throws Exception {
    MinioClient internalClient = mock(MinioClient.class);
    MinioClient publicClient = mock(MinioClient.class);
    when(publicClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
        .thenReturn("https://files.example.test/delivery/test.txt?signature=public");
    MinioObjectStorage storage = new MinioObjectStorage(
        internalClient, publicClient, "delivery");

    URI signed = storage.signedDownload("test.txt", Duration.ofMinutes(10));

    assertEquals("files.example.test", signed.getHost());
    verify(publicClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    verify(internalClient, never()).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
  }

  @Test
  void deletesObjectThroughInternalClient() throws Exception {
    MinioClient internalClient = mock(MinioClient.class);
    MinioObjectStorage storage = new MinioObjectStorage(
        internalClient, mock(MinioClient.class), "delivery");

    storage.delete("orphan.txt");

    verify(internalClient).removeObject(any(RemoveObjectArgs.class));
  }
}
