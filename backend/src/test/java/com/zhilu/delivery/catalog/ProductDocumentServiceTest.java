package com.zhilu.delivery.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.clearInvocations;

import com.zhilu.delivery.document.DocumentView;
import com.zhilu.delivery.document.OutlineClient;
import com.zhilu.delivery.document.OutlineConnection;
import com.zhilu.delivery.document.OutlineDocument;
import com.zhilu.delivery.document.OutlineException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:product-documents;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none",
    "delivery.outline.base-url=http://outline.test",
    "delivery.outline.api-token=ol_api_test",
    "delivery.outline.collection-id=a4296a54-2044-4529-ba86-d598a5322e06"
})
class ProductDocumentServiceTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProductDocumentService documents;
  @MockBean private OutlineClient outline;
  private final Map<String, OutlineDocument> remote =
      new ConcurrentHashMap<String, OutlineDocument>();
  private final AtomicLong revision = new AtomicLong();

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[]{"product_feature","product_module","product_version",
        "document_template_config","knowledge_item","outline_document_link","product",
        "app_user","organization"}) jdbc.update("delete from " + table);
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (3200,'产品组织','PRODUCT-DOC')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (3200,3200,'product-owner','产品负责人','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (3200,3200,'ERP','企业财务','ACTIVE')");
    jdbc.update("insert into product_module(id,product_id,parent_id,code,name,status,sort_order) "
        + "values (3201,3200,null,'FIN','财务域','ACTIVE',0)");
    jdbc.update("insert into product_module(id,product_id,parent_id,code,name,status,sort_order) "
        + "values (3202,3200,3201,'AR','应收','ACTIVE',0)");
    jdbc.update("insert into product_feature(id,product_id,module_id,code,name,description,status) "
        + "values (3203,3200,3202,'AR-01','收款核销','自动匹配到账流水','ACTIVE')");
    jdbc.update("insert into knowledge_item(id,organization_id,type,title,summary,content_text,"
        + "visibility,status,owner_user_id) values "
        + "(3290,3200,'TEMPLATE','功能设计 Spec','功能设计模板','# 模板','ORGANIZATION',"
        + "'PUBLISHED',3200)");
    jdbc.update("insert into document_template_config(knowledge_item_id,stage_code,requirement,"
        + "enabled,published_revision,published_title_snapshot,published_markdown_snapshot) "
        + "values (3290,'PRODUCT_FEATURE_SPEC','REQUIRED',true,7,"
        + "'{{功能名称}} · 设计 Spec','# {{产品名称}} / {{模块名称}} / {{功能名称}}\n\n{{功能说明}}')");
    remote.clear();
    revision.set(0);
    when(outline.info(any(OutlineConnection.class), anyString())).thenAnswer(invocation -> {
      OutlineDocument value = remote.get(invocation.getArgument(1));
      if (value == null) throw new OutlineException(OutlineException.Type.NOT_FOUND, "missing");
      return value;
    });
    when(outline.create(any(OutlineConnection.class), anyString(), anyString(), anyString(),
        anyString(), nullable(String.class), anyBoolean())).thenAnswer(invocation -> {
      OutlineDocument value = new OutlineDocument(
          invocation.getArgument(1), invocation.getArgument(4), invocation.getArgument(5),
          invocation.getArgument(2), invocation.getArgument(3),
          "/doc/" + invocation.getArgument(1), "url-id", revision.incrementAndGet(), Instant.now());
      remote.put(value.getId(), value);
      return value;
    });
    when(outline.update(any(OutlineConnection.class), anyString(), anyString(), anyString()))
        .thenAnswer(invocation -> {
          OutlineDocument current = remote.get(invocation.getArgument(1));
          OutlineDocument value = new OutlineDocument(current.getId(), current.getCollectionId(),
              current.getParentDocumentId(), invocation.getArgument(2), invocation.getArgument(3),
              current.getUrl(), current.getUrlId(), revision.incrementAndGet(), Instant.now());
          remote.put(value.getId(), value);
          return value;
        });
  }

  @Test
  void createsProductModuleTreeAndFeatureSpecFromPublishedTemplate() {
    DocumentView spec = documents.syncFeature(3200, 3200, 3203);

    assertEquals("收款核销 · 设计 Spec", spec.getTitle());
    assertEquals("# 企业财务 / 应收 / 收款核销\n\n自动匹配到账流水", spec.getMarkdown());
    Map<String,Object> stored = jdbc.queryForMap(
        "select outline_link_id,source_template_id,source_template_revision "
            + "from product_feature where id=3203");
    assertEquals(3290L, ((Number) stored.get("source_template_id")).longValue());
    assertEquals(7L, ((Number) stored.get("source_template_revision")).longValue());
    assertEquals(5, remote.size());

    documents.syncFeature(3200, 3200, 3203);
    verify(outline, times(5)).create(any(OutlineConnection.class), anyString(), anyString(),
        anyString(), anyString(), nullable(String.class), anyBoolean());
  }

  @Test
  void initializesAnotherFeatureWithoutRecheckingReadyAncestors() {
    documents.syncFeature(3200, 3200, 3203);
    jdbc.update("insert into product_feature(id,product_id,module_id,code,name,description,status) "
        + "values (3204,3200,3202,'AR-02','应收账龄','按区间分析应收余额','ACTIVE')");
    clearInvocations(outline);

    DocumentView spec = documents.ensureFeatureSpec(3200, 3200, 3204);

    assertEquals("应收账龄 · 设计 Spec", spec.getTitle());
    verify(outline, times(1)).create(any(OutlineConnection.class), anyString(), anyString(),
        anyString(), anyString(), nullable(String.class), anyBoolean());
    verify(outline, times(2)).info(any(OutlineConnection.class), anyString());
  }
}
