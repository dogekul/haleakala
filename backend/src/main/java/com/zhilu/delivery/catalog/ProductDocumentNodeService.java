package com.zhilu.delivery.catalog;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.document.DocumentCenterService;
import com.zhilu.delivery.document.DocumentView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductDocumentNodeService {
  private final JdbcTemplate jdbc;
  private final ProductCatalogService catalog;
  private final DocumentCenterService documents;

  public ProductDocumentNodeService(
      JdbcTemplate jdbc, ProductCatalogService catalog, DocumentCenterService documents) {
    this.jdbc = jdbc;
    this.catalog = catalog;
    this.documents = documents;
  }

  public List<Map<String, Object>> nodes(long organizationId, long productId) {
    catalog.product(organizationId, productId);
    return jdbc.query("select n.id,n.product_id,n.parent_id,n.node_type,n.code,n.title,"
            + "n.description,n.sort_order,n.outline_link_id,n.linked_feature_id,n.version,"
            + "coalesce(l.sync_status,'PENDING') sync_status "
            + "from product_document_node n left join outline_document_link l "
            + "on l.id=n.outline_link_id where n.product_id=? "
            + "order by case when n.parent_id is null then 0 else 1 end,n.sort_order,n.id",
        (row, index) -> nodeRow(row), productId);
  }

  public Map<String, Object> saveNode(long organizationId, long actorId, long productId,
      Long id, Long parentId, String nodeType, String code, String title,
      String description, int sortOrder, Long linkedFeatureId, long version) {
    catalog.product(organizationId, productId);
    String type = nodeType.trim().toUpperCase(java.util.Locale.ROOT);
    if (!"FOLDER".equals(type) && !"DOCUMENT".equals(type)) {
      throw new IllegalArgumentException("节点类型仅支持 FOLDER 或 DOCUMENT");
    }
    if ("FOLDER".equals(type) && linkedFeatureId != null) {
      throw new IllegalArgumentException("文件夹不能关联产品功能");
    }
    validateParent(productId, id, parentId);
    validateFeature(productId, linkedFeatureId);
    String normalizedCode = code.trim();
    String normalizedTitle = title.trim();
    try {
      if (id == null) {
        jdbc.update("insert into product_document_node(product_id,parent_id,node_type,code,title,"
                + "description,sort_order,linked_feature_id) values (?,?,?,?,?,?,?,?)",
            productId, parentId, type, normalizedCode, normalizedTitle,
            description, sortOrder, linkedFeatureId);
        id = jdbc.queryForObject(
            "select id from product_document_node where product_id=? and code=?",
            Long.class, productId, normalizedCode);
      } else {
        Map<String, Object> current = documentNode(organizationId, productId, id.longValue());
        if (((Number) current.get("version")).longValue() != version) {
          throw new ConflictException("文档节点已被更新，请刷新后重试");
        }
        int changed = jdbc.update("update product_document_node set parent_id=?,node_type=?,"
                + "code=?,title=?,description=?,sort_order=?,linked_feature_id=?,"
                + "updated_at=current_timestamp,version=version+1 "
                + "where id=? and product_id=? and version=?",
            parentId, type, normalizedCode, normalizedTitle, description, sortOrder,
            linkedFeatureId, id, productId, version);
        if (changed == 0) throw new ConflictException("文档节点已被更新，请刷新后重试");
      }
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("文档节点编码、Outline 文档或关联功能已存在");
    }
    syncNode(organizationId, productId, id.longValue());
    return documentNode(organizationId, productId, id.longValue());
  }

  public Map<String, Object> retry(long organizationId, long productId, long nodeId) {
    documentNode(organizationId, productId, nodeId);
    syncNode(organizationId, productId, nodeId);
    return documentNode(organizationId, productId, nodeId);
  }

  public DocumentView readContent(long organizationId, long productId, long nodeId) {
    Map<String, Object> node = documentNode(organizationId, productId, nodeId);
    requireDocument(node);
    return documents.readLink(requiredLink(node), organizationId);
  }

  public DocumentView saveContent(long organizationId, long productId, long nodeId,
      String title, String markdown, long revision) {
    Map<String, Object> node = documentNode(organizationId, productId, nodeId);
    requireDocument(node);
    DocumentView saved = documents.updateLink(
        requiredLink(node), organizationId, title, markdown, revision);
    jdbc.update("update product_document_node set title=?,updated_at=current_timestamp,"
            + "version=version+1 where id=? and product_id=?",
        saved.getTitle(), nodeId, productId);
    return saved;
  }

  public DocumentView readFeatureSpec(long organizationId, long productId, long featureId) {
    List<Long> ids = jdbc.queryForList(
        "select n.id from product_document_node n join product p on p.id=n.product_id "
            + "where n.product_id=? and n.linked_feature_id=? and p.organization_id=?",
        Long.class, productId, featureId, organizationId);
    if (ids.isEmpty()) throw new NotFoundException("功能尚未关联设计 Spec");
    return readContent(organizationId, productId, ids.get(0).longValue());
  }

  public DocumentView saveFeatureSpec(long organizationId, long productId, long featureId,
      String title, String markdown, long revision) {
    List<Long> ids = jdbc.queryForList(
        "select n.id from product_document_node n join product p on p.id=n.product_id "
            + "where n.product_id=? and n.linked_feature_id=? and p.organization_id=?",
        Long.class, productId, featureId, organizationId);
    if (ids.isEmpty()) throw new NotFoundException("功能尚未关联设计 Spec");
    return saveContent(organizationId, productId, ids.get(0).longValue(),
        title, markdown, revision);
  }

  private void syncNode(long organizationId, long productId, long nodeId) {
    Map<String, Object> node = documentNode(organizationId, productId, nodeId);
    long parentLinkId = node.get("parentId") == null
        ? productRoot(organizationId, productId)
        : requiredLink(documentNode(
            organizationId, productId, ((Number) node.get("parentId")).longValue()));
    Long linkId = node.get("outlineLinkId") == null
        ? null : Long.valueOf(((Number) node.get("outlineLinkId")).longValue());
    String businessKey = linkId == null
        ? "PRODUCT:" + productId + ":DOCUMENT_NODE:" + nodeId
        : jdbc.queryForObject("select business_key from outline_document_link where id=?",
            String.class, linkId);
    try {
      if (linkId == null) {
        linkId = "FOLDER".equals(node.get("nodeType"))
            ? Long.valueOf(documents.ensureIndex(
                organizationId, businessKey, String.valueOf(node.get("title")), parentLinkId))
            : Long.valueOf(documents.createDocument(
                organizationId, businessKey, "PRODUCT_DOCUMENT",
                String.valueOf(node.get("title")), "# " + node.get("title") + "\n", parentLinkId));
      } else {
        DocumentView current = documents.readLink(linkId.longValue(), organizationId);
        if (!String.valueOf(node.get("title")).equals(current.getTitle())) {
          documents.updateLink(linkId.longValue(), organizationId,
              String.valueOf(node.get("title")), current.getMarkdown(), current.getRevision());
        }
        documents.moveBusinessDocument(organizationId, businessKey, parentLinkId);
      }
    } finally {
      if (linkId == null) linkId = documents.findLinkId(organizationId, businessKey);
      if (linkId != null) {
        jdbc.update("update product_document_node set outline_link_id=?,"
            + "updated_at=current_timestamp where id=? and product_id=?", linkId, nodeId, productId);
      }
    }
  }

  private long productRoot(long organizationId, long productId) {
    Map<String, Object> product = catalog.product(organizationId, productId);
    long root = documents.ensureIndex(organizationId, "PRODUCT_ROOT", "产品资料", null);
    return documents.ensureIndex(organizationId, "PRODUCT:" + productId,
        product.get("code") + " · " + product.get("name"), Long.valueOf(root));
  }

  private Map<String, Object> documentNode(
      long organizationId, long productId, long nodeId) {
    List<Map<String, Object>> values = jdbc.query(
        "select n.id,n.product_id,n.parent_id,n.node_type,n.code,n.title,n.description,"
            + "n.sort_order,n.outline_link_id,n.linked_feature_id,n.version,"
            + "coalesce(l.sync_status,'PENDING') sync_status "
            + "from product_document_node n join product p on p.id=n.product_id "
            + "left join outline_document_link l on l.id=n.outline_link_id "
            + "where n.id=? and n.product_id=? and p.organization_id=?",
        (row, index) -> nodeRow(row), nodeId, productId, organizationId);
    if (values.isEmpty()) throw new NotFoundException("文档节点不存在");
    return values.get(0);
  }

  private Map<String, Object> nodeRow(ResultSet row) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("id", row.getLong("id"));
    value.put("productId", row.getLong("product_id"));
    value.put("parentId", nullableLong(row, "parent_id"));
    value.put("nodeType", row.getString("node_type"));
    value.put("code", row.getString("code"));
    value.put("title", row.getString("title"));
    value.put("description", row.getString("description"));
    value.put("sortOrder", row.getInt("sort_order"));
    value.put("outlineLinkId", nullableLong(row, "outline_link_id"));
    value.put("linkedFeatureId", nullableLong(row, "linked_feature_id"));
    value.put("syncStatus", row.getString("sync_status"));
    value.put("version", row.getLong("version"));
    return value;
  }

  private Long nullableLong(ResultSet row, String name) throws SQLException {
    long value = row.getLong(name);
    return row.wasNull() ? null : Long.valueOf(value);
  }

  private void validateParent(long productId, Long nodeId, Long parentId) {
    if (parentId == null) return;
    List<Map<String, Object>> parents = jdbc.queryForList(
        "select id,parent_id,node_type from product_document_node where id=? and product_id=?",
        parentId, productId);
    if (parents.isEmpty()) throw new IllegalArgumentException("父文档节点不属于当前产品");
    if (!"FOLDER".equals(parents.get(0).get("node_type"))) {
      throw new IllegalArgumentException("文档只能放在文件夹下");
    }
    int depth = 1;
    Long cursor = parentId;
    while (cursor != null) {
      if (nodeId != null && nodeId.equals(cursor)) {
        throw new IllegalArgumentException("文档树最多四级且不能成环");
      }
      List<Long> values = jdbc.queryForList(
          "select parent_id from product_document_node where id=? and product_id=?",
          Long.class, cursor, productId);
      cursor = values.isEmpty() ? null : values.get(0);
      if (cursor != null) depth++;
    }
    if (depth >= 4 || (nodeId != null && depth + subtreeHeight(productId, nodeId) > 4)) {
      throw new IllegalArgumentException("文档树最多四级且不能成环");
    }
  }

  private int subtreeHeight(long productId, long nodeId) {
    List<Long> children = jdbc.queryForList(
        "select id from product_document_node where product_id=? and parent_id=?",
        Long.class, productId, nodeId);
    int height = 1;
    for (Long child : children) {
      height = Math.max(height, 1 + subtreeHeight(productId, child.longValue()));
    }
    return height;
  }

  private void validateFeature(long productId, Long featureId) {
    if (featureId == null) return;
    Integer count = jdbc.queryForObject(
        "select count(*) from product_feature where id=? and product_id=?",
        Integer.class, featureId, productId);
    if (count == null || count == 0) {
      throw new IllegalArgumentException("关联功能不属于当前产品");
    }
  }

  private void requireDocument(Map<String, Object> node) {
    if (!"DOCUMENT".equals(node.get("nodeType"))) {
      throw new IllegalArgumentException("文件夹没有可编辑正文");
    }
  }

  private long requiredLink(Map<String, Object> node) {
    Object value = node.get("outlineLinkId");
    if (value == null) throw new ConflictException("文档尚未初始化");
    return ((Number) value).longValue();
  }
}
