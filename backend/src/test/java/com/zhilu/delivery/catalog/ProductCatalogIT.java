package com.zhilu.delivery.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:catalog;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class ProductCatalogIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProductCatalogService catalog;
  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void cleanCatalog() {
    jdbc.update("delete from audit_log");
    jdbc.update("delete from product_version_feature");
    jdbc.update("delete from requirement_product_feature");
    jdbc.update("delete from product_feature");
    jdbc.update("delete from product_module");
    jdbc.update("delete from product_version");
    jdbc.update("delete from product");
    jdbc.update("delete from outline_document_link");
    jdbc.update("delete from user_role");
    jdbc.update("delete from app_user");
    jdbc.update("delete from organization");
    jdbc.update("merge into role(id,code,name,description,built_in,version) key(id) "
        + "values (9,'PRODUCT_OWNER','产品负责人','产品负责人',false,0)");
    jdbc.update("insert into organization(id,name,code) values (350,'智鹿科技','ZHILU-CATALOG')");
    jdbc.update("insert into organization(id,name,code) values (351,'其他组织','OTHER-CATALOG')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (350,350,'admin','系统管理员','ACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (351,351,'outsider','其他用户','ACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (352,350,'product-owner','张负责人','ACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (353,350,'product-manager','产品经理','ACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (354,350,'disabled-owner','停用产品负责人','DISABLED')");
    jdbc.update("insert into user_role(user_id,role_id) "
        + "select 352,id from role where code='PRODUCT_OWNER'");
    jdbc.update("insert into user_role(user_id,role_id) "
        + "select 354,id from role where code='PRODUCT_OWNER'");
    jdbc.update("insert into user_role(user_id,role_id) "
        + "select 351,id from role where code='PRODUCT_OWNER'");
    jdbc.update("insert into user_role(user_id,role_id) "
        + "select 353,id from role where code='PRODUCT_MANAGER'");
  }

  @Test
  void createsProductsAndKeepsVersionsScopedToTheirProduct() throws Exception {
    long firstProduct = createProduct("智鹿 ERP");
    long secondProduct = createProduct("智鹿 CRM");

    String versionJson = mvc.perform(post("/api/v1/products/{id}/versions", firstProduct)
            .with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"versionName\":\"V5.2\",\"releaseDate\":\"2026-07-01\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.productId").value(firstProduct))
        .andExpect(jsonPath("$.versionName").value("V5.2"))
        .andReturn().getResponse().getContentAsString();
    long versionId = new com.fasterxml.jackson.databind.ObjectMapper()
        .readTree(versionJson).get("id").asLong();

    mvc.perform(get("/api/v1/products/{productId}/versions/{versionId}",
            secondProduct, versionId).with(reader()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    assertEquals(Integer.valueOf(3), jdbc.queryForObject(
        "select count(*) from audit_log where organization_id=350 and resource_type in "
            + "('PRODUCT','PRODUCT_VERSION')", Integer.class));
  }

  @Test
  void returnsProductSummaryCountsOnListAndDetail() throws Exception {
    long productId = createProduct("智鹿 ERP");
    jdbc.update("insert into product_module(product_id,code,name,status,sort_order) "
        + "values (?,'CORE','核心模块','ACTIVE',0)", productId);
    Long moduleId = jdbc.queryForObject(
        "select id from product_module where product_id=?", Long.class, productId);
    jdbc.update("insert into product_feature(product_id,module_id,code,name,status) "
        + "values (?,?,'ORDER','订单管理','ACTIVE')", productId, moduleId);
    jdbc.update("insert into product_version(product_id,version_name,release_date,status) "
        + "values (?,'V2.1','2026-07-01','RELEASED')", productId);

    mvc.perform(get("/api/v1/products").with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].moduleCount").value(1))
        .andExpect(jsonPath("$[0].featureCount").value(1))
        .andExpect(jsonPath("$[0].latestVersionName").value("V2.1"))
        .andExpect(jsonPath("$[0].updatedAt").exists());
    mvc.perform(get("/api/v1/products/{id}", productId).with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.moduleCount").value(1))
        .andExpect(jsonPath("$.featureCount").value(1))
        .andExpect(jsonPath("$.latestVersionName").value("V2.1"))
        .andExpect(jsonPath("$.updatedAt").exists());
  }

  @Test
  void rejectsUnsupportedProductStatusAndCrossOrganizationOwner() throws Exception {
    long productId = createProduct("智鹿 ERP");

    mvc.perform(put("/api/v1/products/{id}", productId)
            .with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"ERP\",\"name\":\"智鹿 ERP\",\"status\":\"BROKEN\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

    mvc.perform(post("/api/v1/products")
            .with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"ownerUserId\":351,\"code\":\"CRM\",\"name\":\"智鹿 CRM\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("请选择当前组织内启用的产品负责人"));

    for (long ownerUserId : new long[] {353L, 354L}) {
      mvc.perform(post("/api/v1/products")
              .with(writer()).with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"ownerUserId\":" + ownerUserId + ",\"name\":\"无效负责人产品\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("请选择当前组织内启用的产品负责人"));
    }
  }

  @Test
  void returnsOnlyEligibleOwnerOptionsAndProductOwnerName() throws Exception {
    mvc.perform(get("/api/v1/products/owner-options").with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(352))
        .andExpect(jsonPath("$[0].displayName").value("张负责人"));

    String response = mvc.perform(post("/api/v1/products")
            .with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"ownerUserId\":352,\"name\":\"负责人产品\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ownerUserId").value(352))
        .andExpect(jsonPath("$.ownerName").value("张负责人"))
        .andReturn().getResponse().getContentAsString();
    long productId = new com.fasterxml.jackson.databind.ObjectMapper()
        .readTree(response).get("id").asLong();

    mvc.perform(get("/api/v1/products/{id}", productId).with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ownerName").value("张负责人"));
  }

  @Test
  void generatesUniqueProductCodesAndReportsDuplicateVersionsAsConflicts() throws Exception {
    long productId = createProduct("智鹿 ERP");
    long secondProductId = createProduct("重复产品");
    assertNotEquals(productId, secondProductId);

    String version = "{\"versionName\":\"V5.2\",\"releaseDate\":\"2026-07-01\"}";
    mvc.perform(post("/api/v1/products/{id}/versions", productId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(version))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/v1/products/{id}/versions", productId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(version))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void reportsConflictWhenGeneratedProductCodeMatchesALegacyNumericCode() throws Exception {
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (900000,350,'900001','历史数字编码产品','PLANNING')");
    jdbc.update("alter table product alter column id restart with 900001");

    mvc.perform(post("/api/v1/products")
            .with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"新产品\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.message").value("产品编号生成冲突"));
  }

  @Test
  void scopesProductsAndReturnsOnlyBindableCatalog() throws Exception {
    long active = createProduct("ERP");
    mvc.perform(put("/api/v1/products/{id}", active).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"ERP\",\"name\":\"ERP\",\"status\":\"ACTIVE\",\"version\":0}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/products").with(actor(351L, "product:write")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"OTHER\",\"name\":\"Other\"}"))
        .andExpect(status().isCreated());
    mvc.perform(get("/api/v1/products").param("bindable", "true").with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(active))
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get("/api/v1/products/{id}", active + 1).with(reader()))
        .andExpect(status().isNotFound());
  }

  @Test
  void enforcesProductLifecycleAndOptimisticLocking() throws Exception {
    long productId = createProduct("ERP");

    mvc.perform(put("/api/v1/products/{id}", productId).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"ERP\",\"name\":\"ERP\",\"status\":\"SUNSET\",\"version\":0}"))
        .andExpect(status().isBadRequest());
    mvc.perform(put("/api/v1/products/{id}", productId).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"ERP\",\"name\":\"ERP\",\"description\":\"Core\","
                + "\"status\":\"ACTIVE\",\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Core"))
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(put("/api/v1/products/{id}", productId).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"ERP\",\"name\":\"Stale\",\"status\":\"ACTIVE\",\"version\":0}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("数据已被更新，请刷新后重试"));
    updateProductStatus(productId, "SUNSET", 1).andExpect(status().isOk());
    updateProductStatus(productId, "ARCHIVED", 2).andExpect(status().isOk());
    updateProductStatus(productId, "ARCHIVED", 3).andExpect(status().isConflict());
  }

  @Test
  void returnsConflictBeforeValidatingAStaleProductTransition() throws Exception {
    long productId = createProduct("ERP");
    updateProductStatus(productId, "ACTIVE", 0).andExpect(status().isOk());

    updateProductStatus(productId, "PLANNING", 0)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("数据已被更新，请刷新后重试"));
  }

  @Test
  void returnsConflictBeforeValidatingAStaleVersionTransition() throws Exception {
    long productId = createProduct("ERP");
    long versionId = createVersion(productId, "V1");
    addIncludedFeature(productId, versionId);
    mvc.perform(put("/api/v1/products/{productId}/versions/{versionId}", productId, versionId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"versionName\":\"V1\",\"releaseDate\":\"2026-07-01\","
                + "\"status\":\"RELEASED\",\"version\":0}"))
        .andExpect(status().isOk());

    mvc.perform(put("/api/v1/products/{productId}/versions/{versionId}", productId, versionId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"versionName\":\"V1\",\"status\":\"PLANNING\",\"version\":0}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("数据已被更新，请刷新后重试"));
  }

  @Test
  void rejectsVersionCreationForArchivedProduct() throws Exception {
    long productId = createProduct("ERP");
    updateProductStatus(productId, "ARCHIVED", 0).andExpect(status().isOk());

    mvc.perform(post("/api/v1/products/{id}/versions", productId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"versionName\":\"V1\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("产品已归档，不能修改版本"));
  }

  @Test
  void rejectsVersionUpdateForArchivedProduct() throws Exception {
    long productId = createProduct("ERP");
    long versionId = createVersion(productId, "V1");
    updateProductStatus(productId, "ARCHIVED", 0).andExpect(status().isOk());

    mvc.perform(put("/api/v1/products/{productId}/versions/{versionId}", productId, versionId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"versionName\":\"V1\",\"status\":\"PLANNING\",\"version\":0}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("产品已归档，不能修改版本"));
  }

  @Test
  void rejectsVersionCreationThatWaitsBehindConcurrentArchive() throws Exception {
    long productId = createProduct("ERP");
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    CountDownLatch archived = new CountDownLatch(1);
    CountDownLatch allowArchiveCommit = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    Future<?> archive = executor.submit(() -> transaction.execute(status -> {
      catalog.updateProduct(350L, productId, null, "ERP", null, null, "ARCHIVED", 0L);
      archived.countDown();
      await(allowArchiveCommit);
      return null;
    }));

    try {
      assertTrue(archived.await(5, TimeUnit.SECONDS));
      Future<RuntimeException> creation = executor.submit(() -> {
        try {
          catalog.createVersion(350L, productId, "V1", null);
          return null;
        } catch (RuntimeException exception) {
          return exception;
        }
      });
      assertThrows(TimeoutException.class,
          () -> creation.get(250, TimeUnit.MILLISECONDS));

      allowArchiveCommit.countDown();
      archive.get(5, TimeUnit.SECONDS);
      assertTrue(creation.get(5, TimeUnit.SECONDS) instanceof ConflictException);
      assertEquals(Integer.valueOf(0), jdbc.queryForObject(
          "select count(*) from product_version where product_id=?", Integer.class, productId));
    } finally {
      allowArchiveCommit.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void releasesVersionsOnlyWithDatesAndReturnsOnlyBindableVersions() throws Exception {
    long productId = createProduct("ERP");
    long released = createVersion(productId, "V1");
    addIncludedFeature(productId, released);

    mvc.perform(put("/api/v1/products/{productId}/versions/{versionId}", productId, released)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"versionName\":\"V1\",\"status\":\"RELEASED\",\"version\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("已发布版本必须填写发布日期"));
    mvc.perform(put("/api/v1/products/{productId}/versions/{versionId}", productId, released)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"versionName\":\"V1\",\"releaseDate\":\"2026-07-01\","
                + "\"status\":\"RELEASED\",\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    createVersion(productId, "V2");

    mvc.perform(get("/api/v1/products/{productId}/versions", productId)
            .param("bindable", "true").with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(released))
        .andExpect(jsonPath("$.length()").value(1));
  }

  private long createProduct(String name) throws Exception {
    String json = mvc.perform(post("/api/v1/products")
            .with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + name + "\",\"category\":\"企业应用\"}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    com.fasterxml.jackson.databind.JsonNode product =
        new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
    long id = product.get("id").asLong();
    assertEquals(String.valueOf(id), product.get("code").asText());
    return id;
  }

  private long createVersion(long productId, String versionName) throws Exception {
    String json = mvc.perform(post("/api/v1/products/{id}/versions", productId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"versionName\":\"" + versionName + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PLANNING"))
        .andReturn().getResponse().getContentAsString();
    return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("id").asLong();
  }

  private void addIncludedFeature(long productId, long versionId) {
    String code = "RELEASE-" + versionId;
    jdbc.update("insert into product_module(product_id,code,name,status) "
        + "values (?,?,?,'ACTIVE')", productId, code, code);
    Long moduleId = jdbc.queryForObject(
        "select id from product_module where product_id=? and code=?",
        Long.class, productId, code);
    jdbc.update("insert into product_feature(product_id,module_id,code,name,status) "
        + "values (?,?,?,?,'ACTIVE')", productId, moduleId, code, code);
    Long featureId = jdbc.queryForObject(
        "select id from product_feature where product_id=? and code=?",
        Long.class, productId, code);
    jdbc.update("insert into product_version_feature(product_version_id,product_feature_id,"
        + "availability) values (?,?,'INCLUDED')", versionId, featureId);
  }

  private void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for concurrent catalog operation");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for concurrent catalog operation",
          interrupted);
    }
  }

  private org.springframework.test.web.servlet.ResultActions updateProductStatus(
      long productId, String status, long version) throws Exception {
    return mvc.perform(put("/api/v1/products/{id}", productId).with(writer()).with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"code\":\"ERP\",\"name\":\"ERP\",\"status\":\"" + status
            + "\",\"version\":" + version + "}"));
  }

  private RequestPostProcessor reader() {
    return actor(350L, "product:read");
  }

  private RequestPostProcessor writer() {
    return actor(350L, "product:write");
  }

  private RequestPostProcessor actor(long organizationId, String permission) {
    CurrentUser principal = new CurrentUser(organizationId, organizationId, "admin", "系统管理员",
        Collections.singletonList("ADMIN"), Collections.singletonList(permission));
    return authentication(new UsernamePasswordAuthenticationToken(principal, null,
        Collections.singletonList(new SimpleGrantedAuthority(permission))));
  }
}
