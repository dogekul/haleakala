package com.zhilu.delivery.storage;

import com.zhilu.delivery.common.error.NotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JdbcFileService implements FileService {
  private final JdbcTemplate jdbc;
  private final ObjectStorage objects;
  private final long maxFileSize;

  public JdbcFileService(
      JdbcTemplate jdbc,
      ObjectStorage objects,
      @Value("${delivery.storage.max-file-size:104857600}") long maxFileSize) {
    this.jdbc = jdbc;
    this.objects = objects;
    this.maxFileSize = maxFileSize;
  }

  @Override
  @Transactional
  public FileObjectView store(
      InputStream content,
      String fileName,
      String mimeType,
      long size,
      long organizationId,
      long actorUserId) {
    validate(fileName, mimeType, size);
    String objectKey = objectKey(organizationId);
    String checksum = putAndDigest(objectKey, content, size, mimeType);
    jdbc.update("insert into file_object(organization_id,object_key,original_name,mime_type,"
            + "size_bytes,checksum_sha256,file_version,created_by) values (?,?,?,?,?,?,1,?)",
        organizationId, objectKey, fileName.trim(), mimeType, size, checksum, actorUserId);
    Long fileId = jdbc.queryForObject(
        "select id from file_object where object_key=?", Long.class, objectKey);
    jdbc.update("insert into file_version(file_id,version_no,object_key,mime_type,size_bytes,"
            + "checksum_sha256,created_by) values (?,1,?,?,?,?,?)",
        fileId, objectKey, mimeType, size, checksum, actorUserId);
    return file(fileId);
  }

  @Override
  public URI signedDownload(long fileId, Duration ttl) {
    if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofHours(1)) > 0) {
      throw new IllegalArgumentException("下载链接有效期必须在 1 秒至 1 小时之间");
    }
    return objects.signedDownload(file(fileId).getObjectKey(), ttl);
  }

  @Override
  @Transactional
  public FileObjectView addVersion(
      long fileId, InputStream content, String mimeType, long size, long actorUserId) {
    FileObjectView current = file(fileId);
    validate(current.getOriginalName(), mimeType, size);
    String objectKey = objectKey(current.getOrganizationId());
    String checksum = putAndDigest(objectKey, content, size, mimeType);
    int next = current.getFileVersion() + 1;
    jdbc.update("insert into file_version(file_id,version_no,object_key,mime_type,size_bytes,"
            + "checksum_sha256,created_by) values (?,?,?,?,?,?,?)",
        fileId, next, objectKey, mimeType, size, checksum, actorUserId);
    jdbc.update("update file_object set object_key=?,mime_type=?,size_bytes=?,checksum_sha256=?,"
            + "file_version=?,updated_at=current_timestamp,version=version+1 where id=?",
        objectKey, mimeType, size, checksum, next, fileId);
    return file(fileId);
  }

  private FileObjectView file(long id) {
    List<FileObjectView> values = jdbc.query(
        "select id,organization_id,object_key,original_name,mime_type,size_bytes,"
            + "checksum_sha256,file_version from file_object where id=?",
        (row, index) -> new FileObjectView(
            row.getLong("id"), row.getLong("organization_id"), row.getString("object_key"),
            row.getString("original_name"), row.getString("mime_type"),
            row.getLong("size_bytes"), row.getString("checksum_sha256"),
            row.getInt("file_version")), id);
    if (values.isEmpty()) {
      throw new NotFoundException("文件不存在");
    }
    return values.get(0);
  }

  private void validate(String fileName, String mimeType, long size) {
    if (fileName == null || fileName.trim().isEmpty()) {
      throw new IllegalArgumentException("文件名不能为空");
    }
    if (mimeType == null || mimeType.trim().isEmpty()) {
      throw new IllegalArgumentException("文件 MIME 类型不能为空");
    }
    if (size < 0 || size > maxFileSize) {
      throw new IllegalArgumentException("文件大小超过限制");
    }
    String lower = fileName.toLowerCase(java.util.Locale.ROOT);
    if (lower.endsWith(".exe") || lower.endsWith(".bat")
        || "application/x-msdownload".equalsIgnoreCase(mimeType)) {
      throw new IllegalArgumentException("不允许上传可执行文件");
    }
  }

  private String putAndDigest(String objectKey, InputStream content, long size, String mimeType) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      DigestInputStream input = new DigestInputStream(content, digest);
      objects.put(objectKey, input, size, mimeType);
      byte[] bytes = digest.digest();
      StringBuilder value = new StringBuilder(64);
      for (byte item : bytes) {
        value.append(String.format("%02x", item & 0xff));
      }
      return value.toString();
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private String objectKey(long organizationId) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    return String.format("organization/%d/%04d/%02d/%s", organizationId, today.getYear(),
        today.getMonthValue(), UUID.randomUUID().toString());
  }
}
