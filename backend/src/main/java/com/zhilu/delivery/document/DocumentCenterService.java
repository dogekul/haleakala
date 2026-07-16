package com.zhilu.delivery.document;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentCenterService {
  private final JdbcTemplate jdbc;
  private final OutlineClient outline;
  private final OutlineProperties properties;
  private final MarkdownRenderer renderer;

  public DocumentCenterService(
      JdbcTemplate jdbc, OutlineClient outline, OutlineProperties properties,
      MarkdownRenderer renderer) {
    this.jdbc = jdbc;
    this.outline = outline;
    this.properties = properties;
    this.renderer = renderer;
  }

  public long ensureIndex(
      long organizationId, String businessKey, String title, Long parentLinkId) {
    long linkId = ensureLink(
        organizationId, businessKey, "INDEX", title, parentLinkId);
    Link link = link(linkId, organizationId);
    if (!blank(link.documentId)) {
      readLink(linkId, organizationId);
      return linkId;
    }
    claimCreation(linkId);
    try {
      OutlineDocument created = createOrRecover(
          organizationId, businessKey, title, "",
          parentDocumentId(parentLinkId, organizationId));
      sync(linkId, created);
      return linkId;
    } catch (OutlineException failure) {
      fail(linkId, failure);
      throw failure;
    }
  }

  public long createDocument(
      long organizationId, String businessKey, String purpose, String title, String markdown,
      long parentLinkId) {
    long linkId = ensureLink(
        organizationId, businessKey, purpose, title, Long.valueOf(parentLinkId));
    Link link = link(linkId, organizationId);
    if (!blank(link.documentId)) {
      readLink(linkId, organizationId);
      return linkId;
    }
    claimCreation(linkId);
    try {
      OutlineDocument created = createOrRecover(
          organizationId, businessKey, title, value(markdown),
          parentDocumentId(Long.valueOf(parentLinkId), organizationId));
      sync(linkId, created);
      return linkId;
    } catch (OutlineException failure) {
      fail(linkId, failure);
      throw failure;
    }
  }

  public Long findLinkId(long organizationId, String businessKey) {
    List<Long> values = jdbc.queryForList(
        "select id from outline_document_link where organization_id=? and business_key=?",
        Long.class, organizationId, businessKey);
    return values.isEmpty() ? null : values.get(0);
  }

  public DocumentView readLink(long linkId, long organizationId) {
    Link link = link(linkId, organizationId);
    if (blank(link.documentId)) {
      throw new ConflictException("文档尚未初始化");
    }
    try {
      OutlineDocument document = outline.info(link.documentId);
      sync(linkId, document);
      return view(linkId, document, "READY", null);
    } catch (OutlineException failure) {
      fail(linkId, failure);
      throw failure;
    }
  }

  @Transactional
  public DocumentView updateLink(
      long linkId, long organizationId, String title, String markdown, long expectedRevision) {
    Link link = lockedLink(linkId, organizationId);
    if (blank(link.documentId)) {
      throw new ConflictException("文档尚未初始化");
    }
    try {
      OutlineDocument current = outline.info(link.documentId);
      sync(linkId, current);
      if (current.getRevision() != expectedRevision) {
        throw new ConflictException("文档已在 Outline 中更新，请刷新后合并");
      }
      OutlineDocument updated = outline.update(link.documentId, title, value(markdown));
      sync(linkId, updated);
      return view(linkId, updated, "READY", null);
    } catch (OutlineException failure) {
      fail(linkId, failure);
      throw failure;
    }
  }

  public DocumentView readKnowledge(long knowledgeId, CurrentUser user) {
    return readLink(knowledgeLink(knowledgeId, user, false), user.getOrganizationId());
  }

  @Transactional
  public DocumentView updateKnowledge(
      long knowledgeId, String title, String markdown, long revision, CurrentUser user) {
    return updateLink(
        knowledgeLink(knowledgeId, user, true), user.getOrganizationId(),
        title, markdown, revision);
  }

  public DocumentView readProjectDocument(
      long projectId, long projectDocumentId, CurrentUser user) {
    assertProjectAccess(projectId, user);
    return readLink(
        projectDocumentLink(projectId, projectDocumentId), user.getOrganizationId());
  }

  @Transactional
  public DocumentView updateProjectDocument(
      long projectId, long projectDocumentId, String title, String markdown, long revision,
      CurrentUser user) {
    assertProjectAccess(projectId, user);
    return updateLink(
        projectDocumentLink(projectId, projectDocumentId), user.getOrganizationId(),
        title, markdown, revision);
  }

  private long ensureLink(
      long organizationId, String businessKey, String purpose, String title, Long parentLinkId) {
    List<Long> existing = jdbc.queryForList(
        "select id from outline_document_link where organization_id=? and business_key=?",
        Long.class, organizationId, businessKey);
    if (!existing.isEmpty()) return existing.get(0);
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("organization_id", organizationId);
    values.put("business_key", businessKey);
    values.put("purpose", purpose);
    values.put("outline_collection_id", properties.getCollectionId());
    values.put("parent_link_id", parentLinkId);
    values.put("title_cache", title);
    values.put("sync_status", "PENDING");
    try {
      return new SimpleJdbcInsert(jdbc).withTableName("outline_document_link")
          .usingColumns(values.keySet().toArray(new String[values.size()]))
          .usingGeneratedKeyColumns("id").executeAndReturnKey(values).longValue();
    } catch (DuplicateKeyException concurrentInsert) {
      return jdbc.queryForObject(
          "select id from outline_document_link where organization_id=? and business_key=?",
          Long.class, organizationId, businessKey);
    }
  }

  private long knowledgeLink(long knowledgeId, CurrentUser user, boolean write) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select outline_link_id,status,owner_user_id from knowledge_item "
            + "where id=? and organization_id=?",
        knowledgeId, user.getOrganizationId());
    if (values.isEmpty()) throw new NotFoundException("知识条目不存在");
    Map<String, Object> item = values.get(0);
    long ownerId = ((Number) item.get("owner_user_id")).longValue();
    boolean manager = user.getPermissions().contains("system:manage");
    if (write && ownerId != user.getId() && !manager) {
      throw new NotFoundException("知识条目不存在");
    }
    if (!write && !"PUBLISHED".equals(item.get("status"))
        && ownerId != user.getId() && !manager) {
      throw new NotFoundException("知识条目不存在");
    }
    Object linkId = item.get("outline_link_id");
    if (linkId == null) throw new ConflictException("知识文档尚未初始化");
    return ((Number) linkId).longValue();
  }

  private void claimCreation(long linkId) {
    Timestamp staleBefore = Timestamp.from(
        Instant.now().minus(properties.getStaleAfter()));
    int changed = jdbc.update(
        "update outline_document_link set sync_status='CREATING',updated_at=current_timestamp,"
            + "version=version+1 where id=? and outline_document_id is null "
            + "and (sync_status in ('PENDING','FAILED') "
            + "or (sync_status='CREATING' and updated_at<?))",
        linkId, staleBefore);
    if (changed == 0) {
      throw new ConflictException("文档正在初始化，请稍后重试");
    }
  }

  private OutlineDocument createOrRecover(
      long organizationId, String businessKey, String title, String markdown,
      String parentDocumentId) {
    String documentId = deterministicDocumentId(organizationId, businessKey);
    try {
      OutlineDocument existing = outline.info(documentId);
      if (existing != null) return existing;
    } catch (OutlineException failure) {
      if (failure.getType() != OutlineException.Type.NOT_FOUND) throw failure;
    }
    return outline.create(
        documentId, title, markdown, properties.getCollectionId(), parentDocumentId, true);
  }

  static String deterministicDocumentId(long organizationId, String businessKey) {
    return UUID.nameUUIDFromBytes(
        ("zhilu-outline:" + organizationId + ":" + businessKey)
            .getBytes(StandardCharsets.UTF_8)).toString();
  }

  private long projectDocumentLink(long projectId, long projectDocumentId) {
    List<Long> values = jdbc.queryForList(
        "select outline_link_id from project_document where id=? and project_id=?",
        Long.class, projectDocumentId, projectId);
    if (values.isEmpty()) throw new NotFoundException("项目文档不存在");
    Long linkId = values.get(0);
    if (linkId == null) throw new ConflictException("项目文档尚未初始化");
    return linkId.longValue();
  }

  private void assertProjectAccess(long projectId, CurrentUser user) {
    Integer project = jdbc.queryForObject(
        "select count(*) from delivery_project where id=? and organization_id=?",
        Integer.class, projectId, user.getOrganizationId());
    if (project == null || project == 0) {
      throw new NotFoundException("项目不存在或无权访问");
    }
    if (user.getRoles().contains("ADMIN") || user.getRoles().contains("PMO")) return;
    Integer member = jdbc.queryForObject(
        "select count(*) from project_member where project_id=? and user_id=?",
        Integer.class, projectId, user.getId());
    if (member == null || member == 0) {
      throw new NotFoundException("项目不存在或无权访问");
    }
  }

  private String parentDocumentId(Long parentLinkId, long organizationId) {
    if (parentLinkId == null) return null;
    Link parent = link(parentLinkId.longValue(), organizationId);
    if (blank(parent.documentId)) throw new ConflictException("父目录尚未初始化");
    return parent.documentId;
  }

  private Link link(long linkId, long organizationId) {
    List<Link> values = jdbc.query(
        "select id,organization_id,outline_document_id,title_cache,revision,sync_status,"
            + "last_error,outline_updated_at from outline_document_link "
            + "where id=? and organization_id=?",
        (row, index) -> link(row), linkId, organizationId);
    if (values.isEmpty()) throw new NotFoundException("文档不存在");
    return values.get(0);
  }

  private Link lockedLink(long linkId, long organizationId) {
    List<Link> values = jdbc.query(
        "select id,organization_id,outline_document_id,title_cache,revision,sync_status,"
            + "last_error,outline_updated_at from outline_document_link "
            + "where id=? and organization_id=? for update",
        (row, index) -> link(row), linkId, organizationId);
    if (values.isEmpty()) throw new NotFoundException("文档不存在");
    return values.get(0);
  }

  private Link link(ResultSet row) throws SQLException {
    return new Link(
        row.getLong("id"), row.getLong("organization_id"),
        row.getString("outline_document_id"), row.getString("title_cache"),
        nullableLong(row, "revision"), row.getString("sync_status"),
        row.getString("last_error"), instant(row.getTimestamp("outline_updated_at")));
  }

  private void sync(long linkId, OutlineDocument document) {
    jdbc.update("update outline_document_link set outline_collection_id=?,outline_document_id=?,"
            + "outline_url_id=?,title_cache=?,summary_cache=?,revision=?,outline_updated_at=?,"
            + "sync_status='READY',retry_count=0,last_error=null,updated_at=current_timestamp,"
            + "version=version+1 where id=?",
        document.getCollectionId(), document.getId(), document.getUrlId(), document.getTitle(),
        summary(document.getText()), document.getRevision(), timestamp(document.getUpdatedAt()),
        linkId);
  }

  private void fail(long linkId, OutlineException failure) {
    jdbc.update("update outline_document_link set sync_status='FAILED',retry_count=retry_count+1,"
            + "last_error=?,updated_at=current_timestamp,version=version+1 where id=?",
        truncate(failure.getMessage(), 1000), linkId);
  }

  private DocumentView view(
      long linkId, OutlineDocument document, String syncStatus, String lastError) {
    return new DocumentView(
        linkId, document.getTitle(), document.getText(),
        renderer.renderFragment(document.getText()), document.getRevision(),
        document.getUpdatedAt(), syncStatus, lastError, outlineUrl(document));
  }

  private String outlineUrl(OutlineDocument document) {
    if (blank(document.getUrl())) return null;
    if (document.getUrl().startsWith("http://") || document.getUrl().startsWith("https://")) {
      return document.getUrl();
    }
    String base = browserBaseUrl();
    if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    return base + (document.getUrl().startsWith("/") ? "" : "/") + document.getUrl();
  }

  private String browserBaseUrl() {
    String value = properties.getPublicBaseUrl();
    return blank(value) ? properties.getBaseUrl().trim() : value.trim();
  }

  private String summary(String markdown) {
    String compact = value(markdown).replaceAll("\\s+", " ").trim();
    return truncate(compact, 1000);
  }

  private String truncate(String value, int length) {
    if (value == null || value.length() <= length) return value;
    return value.substring(0, length);
  }

  private String value(String value) {
    return value == null ? "" : value;
  }

  private boolean blank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private Long nullableLong(ResultSet row, String name) throws SQLException {
    Object value = row.getObject(name);
    return value == null ? null : ((Number) value).longValue();
  }

  private Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private static final class Link {
    private final long id;
    private final long organizationId;
    private final String documentId;
    private final String title;
    private final Long revision;
    private final String syncStatus;
    private final String lastError;
    private final Instant updatedAt;

    private Link(
        long id, long organizationId, String documentId, String title, Long revision,
        String syncStatus, String lastError, Instant updatedAt) {
      this.id = id;
      this.organizationId = organizationId;
      this.documentId = documentId;
      this.title = title;
      this.revision = revision;
      this.syncStatus = syncStatus;
      this.lastError = lastError;
      this.updatedAt = updatedAt;
    }
  }
}
