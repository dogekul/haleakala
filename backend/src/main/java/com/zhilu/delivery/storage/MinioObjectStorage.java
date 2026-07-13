package com.zhilu.delivery.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MinioObjectStorage implements ObjectStorage {
  private final MinioClient client;
  private final String bucket;

  public MinioObjectStorage(
      MinioClient client, @Value("${delivery.storage.bucket}") String bucket) {
    this.client = client;
    this.bucket = bucket;
  }

  private void ensureBucket() {
    try {
      BucketExistsArgs exists = BucketExistsArgs.builder().bucket(bucket).build();
      if (!client.bucketExists(exists)) {
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
      }
    } catch (Exception error) {
      throw new IllegalStateException("初始化对象存储失败", error);
    }
  }

  @Override
  public void put(String objectKey, InputStream content, long size, String mimeType) {
    try {
      ensureBucket();
      client.putObject(PutObjectArgs.builder()
          .bucket(bucket)
          .object(objectKey)
          .stream(content, size, -1)
          .contentType(mimeType)
          .build());
    } catch (Exception error) {
      throw new IllegalStateException("文件写入对象存储失败", error);
    }
  }

  @Override
  public URI signedDownload(String objectKey, Duration ttl) {
    try {
      String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
          .method(Method.GET)
          .bucket(bucket)
          .object(objectKey)
          .expiry((int) ttl.getSeconds())
          .build());
      return URI.create(url);
    } catch (Exception error) {
      throw new IllegalStateException("生成下载链接失败", error);
    }
  }
}
