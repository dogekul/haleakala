package com.zhilu.delivery.storage;

import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.iam.service.CurrentUser;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class JdbcFileService implements FileService {
  private final JdbcTemplate jdbc;
  private final ObjectStorage objects;
  private final AuditService audit;
  private final long maxFileSize;

  public JdbcFileService(
      JdbcTemplate jdbc,
      ObjectStorage objects,
      AuditService audit,
      @Value("${delivery.storage.max-file-size:104857600}") long maxFileSize) {
    this.jdbc = jdbc;
    this.objects = objects;
    this.audit = audit;
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
    assertActorOrganization(organizationId, actorUserId);
    validate(fileName, mimeType, size);
    String objectKey = objectKey(organizationId);
    String checksum = putAndDigest(objectKey, content, size, mimeType);
    deleteObjectOnRollback(objectKey);
    jdbc.update("insert into file_object(organization_id,object_key,original_name,mime_type,"
            + "size_bytes,checksum_sha256,file_version,created_by) values (?,?,?,?,?,?,1,?)",
        organizationId, objectKey, fileName.trim(), mimeType, size, checksum, actorUserId);
    Long fileId = jdbc.queryForObject(
        "select id from file_object where object_key=?", Long.class, objectKey);
    jdbc.update("insert into file_version(file_id,version_no,object_key,mime_type,size_bytes,"
            + "checksum_sha256,created_by) values (?,1,?,?,?,?,?)",
        fileId, objectKey, mimeType, size, checksum, actorUserId);
    return file(fileId, organizationId);
  }

  @Override
  @Transactional
  public FileObjectView store(
      InputStream content, String fileName, String mimeType, long size, CurrentUser user) {
    requireFileWrite(user);
    return store(content, fileName, mimeType, size, user.getOrganizationId(), user.getId());
  }

  @Override
  public URI signedDownload(long fileId, Duration ttl, CurrentUser user) {
    if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofHours(1)) > 0) {
      throw new IllegalArgumentException("下载链接有效期必须在 1 秒至 1 小时之间");
    }
    FileObjectView current = accessibleFile(fileId, user, false);
    URI signed = objects.signedDownload(current.getObjectKey(), ttl);
    audit.record(current.getOrganizationId(), user.getId(), "FILE_DOWNLOADED", "FILE_OBJECT",
        String.valueOf(fileId), "生成文件下载链接");
    return signed;
  }

  @Override
  @Transactional
  public FileObjectView addVersion(
      long fileId, InputStream content, String mimeType, long size, CurrentUser user) {
    requireFileWrite(user);
    FileObjectView current = accessibleFile(fileId, user, true);
    validate(current.getOriginalName(), mimeType, size);
    String objectKey = objectKey(current.getOrganizationId());
    String checksum = putAndDigest(objectKey, content, size, mimeType);
    deleteObjectOnRollback(objectKey);
    int next = current.getFileVersion() + 1;
    jdbc.update("insert into file_version(file_id,version_no,object_key,mime_type,size_bytes,"
            + "checksum_sha256,created_by) values (?,?,?,?,?,?,?)",
        fileId, next, objectKey, mimeType, size, checksum, user.getId());
    jdbc.update("update file_object set object_key=?,mime_type=?,size_bytes=?,checksum_sha256=?,"
            + "file_version=?,updated_at=current_timestamp,version=version+1 where id=?",
        objectKey, mimeType, size, checksum, next, fileId);
    audit.record(current.getOrganizationId(), user.getId(), "FILE_VERSION_ADDED", "FILE_OBJECT",
        String.valueOf(fileId), "新增文件版本 " + next);
    return file(fileId, user.getOrganizationId());
  }

  private FileObjectView file(long id, long organizationId) {
    List<FileObjectView> values = jdbc.query(
        "select id,organization_id,object_key,original_name,mime_type,size_bytes,"
            + "checksum_sha256,file_version from file_object where id=? and organization_id=?",
        (row, index) -> new FileObjectView(
            row.getLong("id"), row.getLong("organization_id"), row.getString("object_key"),
            row.getString("original_name"), row.getString("mime_type"),
            row.getLong("size_bytes"), row.getString("checksum_sha256"),
            row.getInt("file_version")), id, organizationId);
    if (values.isEmpty()) {
      throw new NotFoundException("文件不存在");
    }
    return values.get(0);
  }

  private FileObjectView accessibleFile(long fileId, CurrentUser user, boolean write) {
    FileObjectView current = file(fileId, user.getOrganizationId());
    if (hasCrossProjectScope(user)) return current;
    Integer artifacts = jdbc.queryForObject(
        "select count(*) from project_artifact where file_id=?", Integer.class, fileId);
    if (artifacts == null || artifacts == 0) return current;
    Integer memberships = jdbc.queryForObject(
        "select count(*) from project_artifact a "
            + "join delivery_project p on p.id=a.project_id "
            + "join project_member m on m.project_id=p.id and m.user_id=? "
            + "where a.file_id=? and p.organization_id=?",
        Integer.class, user.getId(), fileId, user.getOrganizationId());
    if (memberships != null && memberships > 0) return current;
    if (!write && isDiscoverableKnowledge(fileId, user)) return current;
    throw new NotFoundException("文件不存在或无权访问");
  }

  private boolean isDiscoverableKnowledge(long fileId, CurrentUser user) {
    Integer count = jdbc.queryForObject(
        "select count(*) from training_material t "
            + "join knowledge_item k on k.id=t.knowledge_item_id "
            + "where t.file_object_id=? and k.organization_id=? "
            + "and (k.status='PUBLISHED' or k.owner_user_id=?)",
        Integer.class, fileId, user.getOrganizationId(), user.getId());
    return count != null && count > 0;
  }

  private boolean hasCrossProjectScope(CurrentUser user) {
    return user.getRoles().contains("ADMIN") || user.getRoles().contains("PMO");
  }

  private void requireFileWrite(CurrentUser user) {
    if (user == null || !user.getPermissions().contains("file:write")) {
      throw new AccessDeniedException("无权写入文件");
    }
  }

  private void assertActorOrganization(long organizationId, long actorUserId) {
    Integer count = jdbc.queryForObject(
        "select count(*) from app_user where id=? and organization_id=? and status='ACTIVE'",
        Integer.class, actorUserId, organizationId);
    if (count == null || count == 0) throw new NotFoundException("文件组织或操作人不存在");
  }

  private void deleteObjectOnRollback(final String objectKey) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status == TransactionSynchronization.STATUS_ROLLED_BACK) objects.delete(objectKey);
      }
    });
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
