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
    "spring.datasource.url=jdbc:h2:mem:product-document-nodes;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none",
    "delivery.outline.base-url=http://outline.test", "delivery.outline.api-token=ol_api_test",
    "delivery.outline.collection-id=a4296a54-2044-4529-ba86-d598a5322e06"
})
@AutoConfigureMockMvc
class ProductDocumentNodeApiIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private OutlineClient outline;
  private final Map<String, OutlineDocument> remote =
      new ConcurrentHashMap<String, OutlineDocument>();
  private final AtomicLong revisions = new AtomicLong();

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[]{"product_document_node","product_feature","product_module",
        "outline_document_link","product","app_user","organization"}) {
      jdbc.update("delete from " + table);
    }
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (3300,'产品组织','PRODUCT-NODES')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (3300,3300,'admin','管理员','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (3300,3300,'CRM','客户平台','ACTIVE')");
    remote.clear();
    revisions.set(0);
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
    when(outline.move(any(OutlineConnection.class), anyString(), anyString()))
        .thenAnswer(invocation -> {
          OutlineDocument current = remote.get(invocation.getArgument(1));
          return store(current.getId(), current.getCollectionId(), invocation.getArgument(2),
              current.getTitle(), current.getText());
        });
  }

  @Test
  void createsListsReadsAndUpdatesIndependentDocuments() throws Exception {
    long folderId = create(null, "FOLDER", "DOC-01", "01 产品总纲", 1);
    long documentId = create(folderId, "DOCUMENT", "DOC-01-01", "产品一页纸", 1);

    mvc.perform(get("/api/v1/products/3300/document-nodes").with(actor("product:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("DOC-01"))
        .andExpect(jsonPath("$[1].parentId").value(folderId))
        .andExpect(jsonPath("$[1].syncStatus").value("READY"));
    mvc.perform(get("/api/v1/products/3300/document-nodes/{id}/content", documentId)
            .with(actor("product:read")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.markdown").value("# 产品一页纸\n"));
    mvc.perform(put("/api/v1/products/3300/document-nodes/{id}/content", documentId)
            .with(actor("product:write")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"产品一页纸\",\"markdown\":\"# 新正文\",\"revision\":4}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.markdown").value("# 新正文"));
  }

  @Test
  void rejectsCrossProductParentsAndFifthLevel() throws Exception {
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (3301,3300,'OTHER','其他产品','ACTIVE')");
    long foreign = createFor(3301, null, "FOLDER", "OTHER-ROOT", "其他目录", 1);
    mvc.perform(post("/api/v1/products/3300/document-nodes").with(actor("product:write"))
            .with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(nodeJson(foreign, "FOLDER", "BAD", "错误目录", 1)))
        .andExpect(status().isBadRequest());

    long parent = nullSafeCreate(null, "L1");
    parent = nullSafeCreate(parent, "L2");
    parent = nullSafeCreate(parent, "L3");
    parent = nullSafeCreate(parent, "L4");
    mvc.perform(post("/api/v1/products/3300/document-nodes").with(actor("product:write"))
            .with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(nodeJson(parent, "FOLDER", "L5", "第五级", 5)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void featureSpecRequiresAnExplicitDocumentLink() throws Exception {
    jdbc.update("insert into product_module(id,product_id,code,name,status) "
        + "values (3310,3300,'SALES','销售','ACTIVE')");
    jdbc.update("insert into product_feature(id,product_id,module_id,code,name,status) "
        + "values (3311,3300,3310,'LEAD','线索','ACTIVE')");
    mvc.perform(get("/api/v1/products/3300/features/3311/spec").with(actor("product:read")))
        .andExpect(status().isNotFound());
  }

  private long nullSafeCreate(Long parentId, String code) throws Exception {
    return create(parentId, "FOLDER", code, code, 1);
  }

  private long create(Long parentId, String type, String code, String title, int sortOrder)
      throws Exception {
    return createFor(3300, parentId, type, code, title, sortOrder);
  }

  private long createFor(long productId, Long parentId, String type, String code,
      String title, int sortOrder) throws Exception {
    String body = mvc.perform(post("/api/v1/products/{id}/document-nodes", productId)
            .with(actor("product:write")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(nodeJson(parentId, type, code, title, sortOrder)))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asLong();
  }

  private String nodeJson(Long parentId, String type, String code, String title, int sortOrder) {
    return "{\"parentId\":" + (parentId == null ? "null" : parentId)
        + ",\"nodeType\":\"" + type + "\",\"code\":\"" + code
        + "\",\"title\":\"" + title + "\",\"sortOrder\":" + sortOrder + "}";
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
