package com.zhilu.delivery.catalog;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.document.DocumentCenterService;
import com.zhilu.delivery.document.DocumentView;
import com.zhilu.delivery.knowledge.KnowledgeService;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductDocumentService {
  private final JdbcTemplate jdbc;
  private final DocumentCenterService documents;

  public ProductDocumentService(JdbcTemplate jdbc, DocumentCenterService documents) {
    this.jdbc = jdbc;
    this.documents = documents;
  }

  public DocumentView syncFeature(long organizationId, long productId, long featureId) {
    Map<String, Object> feature = feature(organizationId, productId, featureId);
    long parentLinkId = syncModuleLink(
        organizationId, productId, ((Number) feature.get("module_id")).longValue());
    String businessKey = featureKey(productId, featureId);
    if (feature.get("outline_link_id") != null) {
      DocumentView current = documents.readBusinessDocument(organizationId, businessKey);
      String title = feature.get("feature_name") + " · 设计 Spec";
      if (!title.equals(current.getTitle())) {
        current = documents.updateBusinessDocument(
            organizationId, businessKey, title, current.getMarkdown(), current.getRevision());
      }
      documents.moveBusinessDocument(organizationId, businessKey, parentLinkId);
      return current;
    }
    Template template = template(organizationId);
    long linkId = documents.createDocument(
        organizationId, businessKey, KnowledgeService.PRODUCT_FEATURE_SPEC,
        replace(template.title, feature), replace(template.markdown, feature), parentLinkId);
    documents.moveBusinessDocument(organizationId, businessKey, parentLinkId);
    bindFeature(organizationId, productId, featureId, linkId, template);
    return documents.readBusinessDocument(organizationId, businessKey);
  }

  public DocumentView ensureFeatureSpec(
      long organizationId, long productId, long featureId) {
    Map<String, Object> feature = feature(organizationId, productId, featureId);
    Long parentLinkId = readyLink(
        organizationId,
        moduleKey(productId, ((Number) feature.get("module_id")).longValue()));
    if (parentLinkId == null) return syncFeature(organizationId, productId, featureId);

    String businessKey = featureKey(productId, featureId);
    if (feature.get("outline_link_id") != null) {
      DocumentView current = documents.readBusinessDocument(organizationId, businessKey);
      String title = feature.get("feature_name") + " · 设计 Spec";
      return title.equals(current.getTitle()) ? current : documents.updateBusinessDocument(
          organizationId, businessKey, title, current.getMarkdown(), current.getRevision());
    }

    Template template = template(organizationId);
    long linkId = documents.createDocument(
        organizationId, businessKey, KnowledgeService.PRODUCT_FEATURE_SPEC,
        replace(template.title, feature), replace(template.markdown, feature),
        parentLinkId.longValue());
    bindFeature(organizationId, productId, featureId, linkId, template);
    return documents.readBusinessDocument(organizationId, businessKey);
  }

  public Map<String, Object> syncAllForProduct(long organizationId, long productId) {
    product(organizationId, productId);
    int completed = 0;
    int failed = 0;
    try { syncProductLink(organizationId, productId); completed++; }
    catch (RuntimeException failure) { failed++; }
    for (Map<String, Object> module : jdbc.queryForList(
        "select id from product_module where product_id=? order by id", productId)) {
      try {
        syncModuleLink(organizationId, productId, ((Number) module.get("id")).longValue());
        completed++;
      } catch (RuntimeException failure) { failed++; }
    }
    for (Map<String, Object> feature : jdbc.queryForList(
        "select id from product_feature where product_id=? order by id", productId)) {
      try {
        syncFeature(organizationId, productId, ((Number) feature.get("id")).longValue());
        completed++;
      } catch (RuntimeException failure) { failed++; }
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("products", 1);
    result.put("completed", completed);
    result.put("failed", failed);
    return result;
  }

  public Map<String, Object> initializeAll(long organizationId) {
    int products = 0;
    int completed = 0;
    int failed = 0;
    for (Long productId : jdbc.queryForList(
        "select id from product where organization_id=? order by id",
        Long.class, organizationId)) {
      Map<String, Object> result = syncAllForProduct(organizationId, productId.longValue());
      products++;
      completed += ((Number) result.get("completed")).intValue();
      failed += ((Number) result.get("failed")).intValue();
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("products", products);
    result.put("completed", completed);
    result.put("failed", failed);
    return result;
  }

  public List<Map<String, Object>> tree(long organizationId, long productId) {
    Map<String, Object> product = product(organizationId, productId);
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    result.add(node("PRODUCT", productId, null,
        product.get("code") + " · " + product.get("name"),
        linkStatus(organizationId, "PRODUCT:" + productId), null));
    for (Map<String, Object> module : jdbc.queryForList(
        "select id,parent_id,code,name from product_module where product_id=? "
            + "order by case when parent_id is null then 0 else 1 end,sort_order,id",
        productId)) {
      long moduleId = ((Number) module.get("id")).longValue();
      result.add(node("MODULE", moduleId,
          module.get("parent_id") == null ? Long.valueOf(productId)
              : Long.valueOf(((Number) module.get("parent_id")).longValue()),
          module.get("code") + " · " + module.get("name"),
          linkStatus(organizationId, moduleKey(productId, moduleId)), null));
    }
    for (Map<String, Object> feature : jdbc.queryForList(
        "select id,module_id,code,name,outline_link_id from product_feature "
            + "where product_id=? order by name,id", productId)) {
      long featureId = ((Number) feature.get("id")).longValue();
      result.add(node("FEATURE", featureId,
          Long.valueOf(((Number) feature.get("module_id")).longValue()),
          feature.get("code") + " · " + feature.get("name"),
          linkStatus(organizationId, featureKey(productId, featureId)),
          Long.valueOf(featureId)));
    }
    return result;
  }

  public long syncProduct(long organizationId, long productId) {
    return syncProductLink(organizationId, productId);
  }

  public long syncModule(long organizationId, long productId, long moduleId) {
    return syncModuleLink(organizationId, productId, moduleId);
  }

  public DocumentView readSpec(long organizationId, long productId, long featureId) {
    feature(organizationId, productId, featureId);
    return documents.readBusinessDocument(organizationId, featureKey(productId, featureId));
  }

  public DocumentView saveSpec(
      long organizationId, long productId, long featureId,
      String title, String markdown, long revision) {
    feature(organizationId, productId, featureId);
    return documents.updateBusinessDocument(
        organizationId, featureKey(productId, featureId), title, markdown, revision);
  }

  private long syncProductLink(long organizationId, long productId) {
    Map<String, Object> product = product(organizationId, productId);
    long root = namedIndex(organizationId, "PRODUCT_ROOT", "产品资料", null);
    return namedIndex(organizationId, "PRODUCT:" + productId,
        product.get("code") + " · " + product.get("name"), Long.valueOf(root));
  }

  private long syncModuleLink(long organizationId, long productId, long moduleId) {
    Map<String, Object> module = module(organizationId, productId, moduleId);
    Object parent = module.get("parent_id");
    long parentLinkId = parent == null
        ? syncProductLink(organizationId, productId)
        : syncModuleLink(organizationId, productId, ((Number) parent).longValue());
    return namedIndex(organizationId, moduleKey(productId, moduleId),
        module.get("code") + " · " + module.get("name"), Long.valueOf(parentLinkId));
  }

  private long namedIndex(
      long organizationId, String businessKey, String title, Long parentLinkId) {
    long linkId = documents.ensureIndex(organizationId, businessKey, title, parentLinkId);
    DocumentView current = documents.readLink(linkId, organizationId);
    if (!title.equals(current.getTitle())) {
      current = documents.updateLink(
          linkId, organizationId, title, current.getMarkdown(), current.getRevision());
    }
    if (parentLinkId != null) {
      documents.moveBusinessDocument(organizationId, businessKey, parentLinkId.longValue());
    }
    return linkId;
  }

  private void bindFeature(
      long organizationId, long productId, long featureId, long linkId, Template template) {
    jdbc.update("update product_feature set outline_link_id=?,source_template_id=?,"
            + "source_template_revision=? where id=? and product_id=? and exists "
            + "(select 1 from product p where p.id=product_feature.product_id "
            + "and p.organization_id=?)",
        linkId, template.id, template.revision, featureId, productId, organizationId);
  }

  private Template template(long organizationId) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select k.id,c.published_revision,c.published_title_snapshot,"
            + "c.published_markdown_snapshot from knowledge_item k "
            + "join document_template_config c on c.knowledge_item_id=k.id "
            + "where k.organization_id=? and k.type='TEMPLATE' and k.status='PUBLISHED' "
            + "and c.stage_code=? and c.requirement='REQUIRED' and c.enabled=true "
            + "and c.published_revision is not null and c.published_title_snapshot is not null "
            + "and c.published_markdown_snapshot is not null order by k.id",
        organizationId, KnowledgeService.PRODUCT_FEATURE_SPEC);
    if (values.isEmpty()) throw new ConflictException("请先发布并启用产品功能设计 Spec 模版");
    if (values.size() > 1) throw new ConflictException("产品功能设计 Spec 模版存在多个");
    Map<String, Object> value = values.get(0);
    return new Template(((Number) value.get("id")).longValue(),
        ((Number) value.get("published_revision")).longValue(),
        String.valueOf(value.get("published_title_snapshot")),
        String.valueOf(value.get("published_markdown_snapshot")));
  }

  private Long readyLink(long organizationId, String businessKey) {
    List<Long> values = jdbc.queryForList(
        "select id from outline_document_link where organization_id=? and business_key=? "
            + "and sync_status='READY' and outline_document_id is not null",
        Long.class, organizationId, businessKey);
    return values.isEmpty() ? null : values.get(0);
  }

  private Map<String, Object> product(long organizationId, long productId) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select id,code,name from product where id=? and organization_id=?",
        productId, organizationId);
    if (values.isEmpty()) throw new NotFoundException("产品不存在");
    return values.get(0);
  }

  private Map<String, Object> module(long organizationId, long productId, long moduleId) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select m.id,m.parent_id,m.code,m.name from product_module m "
            + "join product p on p.id=m.product_id "
            + "where m.id=? and m.product_id=? and p.organization_id=?",
        moduleId, productId, organizationId);
    if (values.isEmpty()) throw new NotFoundException("模块不存在");
    return values.get(0);
  }

  private Map<String, Object> feature(long organizationId, long productId, long featureId) {
    List<Map<String, Object>> values = jdbc.queryForList(
        "select f.id,f.module_id,f.outline_link_id,f.source_template_id,"
            + "f.source_template_revision,f.code feature_code,f.name feature_name,"
            + "f.description feature_description,m.code module_code,m.name module_name,"
            + "p.code product_code,p.name product_name from product_feature f "
            + "join product_module m on m.id=f.module_id "
            + "join product p on p.id=f.product_id "
            + "where f.id=? and f.product_id=? and p.organization_id=?",
        featureId, productId, organizationId);
    if (values.isEmpty()) throw new NotFoundException("功能不存在");
    return values.get(0);
  }

  private String replace(String value, Map<String, Object> feature) {
    return value.replace("{{产品名称}}", text(feature.get("product_name")))
        .replace("{{产品编码}}", text(feature.get("product_code")))
        .replace("{{模块名称}}", text(feature.get("module_name")))
        .replace("{{模块编码}}", text(feature.get("module_code")))
        .replace("{{功能名称}}", text(feature.get("feature_name")))
        .replace("{{功能编码}}", text(feature.get("feature_code")))
        .replace("{{功能说明}}", text(feature.get("feature_description")));
  }

  private String text(Object value) { return value == null ? "" : String.valueOf(value); }
  private Map<String, Object> node(
      String kind, long id, Long parentId, String title, String syncStatus, Long featureId) {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("kind", kind);
    value.put("id", id);
    value.put("parentId", parentId);
    value.put("title", title);
    value.put("syncStatus", syncStatus);
    value.put("featureId", featureId);
    return value;
  }
  private String linkStatus(long organizationId, String businessKey) {
    List<String> values = jdbc.queryForList(
        "select sync_status from outline_document_link where organization_id=? and business_key=?",
        String.class, organizationId, businessKey);
    return values.isEmpty() ? "PENDING" : values.get(0);
  }
  private String moduleKey(long productId, long moduleId) {
    return "PRODUCT:" + productId + ":MODULE:" + moduleId;
  }
  private String featureKey(long productId, long featureId) {
    return "PRODUCT:" + productId + ":FEATURE:" + featureId + ":SPEC";
  }

  private static final class Template {
    private final long id;
    private final long revision;
    private final String title;
    private final String markdown;

    private Template(long id, long revision, String title, String markdown) {
      this.id = id;
      this.revision = revision;
      this.title = title;
      this.markdown = markdown;
    }
  }
}
