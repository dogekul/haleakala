package com.zhilu.delivery.catalog;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhilu.delivery.document.OutlineClient;
import com.zhilu.delivery.document.OutlineConnection;
import com.zhilu.delivery.document.OutlineDocument;
import com.zhilu.delivery.document.OutlineException;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:product-document-api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none",
    "delivery.outline.base-url=http://outline.test", "delivery.outline.api-token=ol_api_test",
    "delivery.outline.collection-id=a4296a54-2044-4529-ba86-d598a5322e06"
})
@AutoConfigureMockMvc
class ProductDocumentApiIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private OutlineClient outline;
  private final Map<String, OutlineDocument> remote =
      new ConcurrentHashMap<String, OutlineDocument>();
  private final AtomicLong revisions = new AtomicLong();

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[]{"product_feature","product_module","document_template_config",
        "knowledge_item","outline_document_link","product","app_user","organization"})
      jdbc.update("delete from " + table);
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (3300,'产品组织','PRODUCT-API')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (3300,3300,'admin','管理员','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (3300,3300,'CRM','客户平台','ACTIVE')");
    jdbc.update("insert into product_module(id,product_id,code,name,status,sort_order) "
        + "values (3301,3300,'SALES','销售','ACTIVE',0)");
    jdbc.update("insert into product_feature(id,product_id,module_id,code,name,description,status) "
        + "values (3302,3300,3301,'LEAD','线索管理','维护线索','ACTIVE')");
    jdbc.update("insert into knowledge_item(id,organization_id,type,title,summary,content_text,"
        + "visibility,status,owner_user_id) values "
        + "(3390,3300,'TEMPLATE','功能 Spec','模板','# 模板','ORGANIZATION','PUBLISHED',3300)");
    jdbc.update("insert into document_template_config(knowledge_item_id,stage_code,requirement,"
        + "enabled,published_revision,published_title_snapshot,published_markdown_snapshot) "
        + "values (3390,'PRODUCT_FEATURE_SPEC','REQUIRED',true,2,"
        + "'{{功能名称}} Spec','# {{功能名称}}')");
    remote.clear(); revisions.set(0);
    when(outline.info(any(OutlineConnection.class), anyString())).thenAnswer(invocation -> {
      OutlineDocument value = remote.get(invocation.getArgument(1));
      if (value == null) throw new OutlineException(OutlineException.Type.NOT_FOUND, "missing");
      return value;
    });
    when(outline.create(any(OutlineConnection.class), anyString(), anyString(), anyString(),
        anyString(), nullable(String.class), anyBoolean())).thenAnswer(invocation -> store(
            invocation.getArgument(1), invocation.getArgument(4), invocation.getArgument(5),
            invocation.getArgument(2), invocation.getArgument(3)));
    when(outline.update(any(OutlineConnection.class), anyString(), anyString(), anyString()))
        .thenAnswer(invocation -> {
          OutlineDocument current = remote.get(invocation.getArgument(1));
          return store(current.getId(), current.getCollectionId(), current.getParentDocumentId(),
              invocation.getArgument(2), invocation.getArgument(3));
        });
  }

  @Test
  void synchronizesOneFeatureSpec() throws Exception {
    mvc.perform(post("/api/v1/products/3300/features/3302/spec/sync")
            .with(actor("product:write")).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("线索管理 Spec"))
        .andExpect(jsonPath("$.markdown").value("# 线索管理"))
        .andExpect(jsonPath("$.revision").isNumber());
  }

  @Test
  void synchronizesListsReadsAndUpdatesFeatureSpecs() throws Exception {
    mvc.perform(post("/api/v1/products/3300/documents/sync").with(actor("product:write"))
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.failed").value(0));
    mvc.perform(get("/api/v1/products/3300/documents").with(actor("product:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].kind").value("PRODUCT"))
        .andExpect(jsonPath("$[2].kind").value("FEATURE"))
        .andExpect(jsonPath("$[2].syncStatus").value("READY"));
    mvc.perform(get("/api/v1/products/3300/features/3302/spec")
            .with(actor("product:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.markdown").value("# 线索管理"));
    mvc.perform(put("/api/v1/products/3300/features/3302/spec")
            .with(actor("product:write")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"线索管理 Spec\",\"markdown\":\"# 新正文\",\"revision\":4}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.markdown").value("# 新正文"));
  }

  @Test
  void initializesAllProductsForAdministrators() throws Exception {
    mvc.perform(post("/api/v1/admin/document-center/initialize-products")
            .with(actor("system:manage")).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.products").value(1))
        .andExpect(jsonPath("$.failed").value(0));
  }

  private OutlineDocument store(
      String id, String collectionId, String parentId, String title, String text) {
    OutlineDocument value = new OutlineDocument(id, collectionId, parentId, title, text,
        "/doc/" + id, "url-id", revisions.incrementAndGet(), Instant.now());
    remote.put(id, value);
    return value;
  }

  private RequestPostProcessor actor(String... permissions) {
    CurrentUser user = new CurrentUser(3300L, 3300L, "admin", "管理员",
        Collections.singletonList("ADMIN"), Arrays.asList(permissions));
    return authentication(new UsernamePasswordAuthenticationToken(user, null,
        Arrays.stream(permissions).map(SimpleGrantedAuthority::new).collect(Collectors.toList())));
  }
}
