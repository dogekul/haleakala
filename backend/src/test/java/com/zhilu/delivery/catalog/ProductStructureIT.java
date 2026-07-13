package com.zhilu.delivery.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    "spring.datasource.url=jdbc:h2:mem:structure;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class ProductStructureIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProductStructureService structures;
  @Autowired private PlatformTransactionManager transactionManager;
  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void cleanStructure() {
    jdbc.update("delete from audit_log");
    jdbc.update("delete from product_version_feature");
    jdbc.update("delete from requirement_product_feature");
    jdbc.update("delete from product_feature");
    jdbc.update("delete from product_module");
    jdbc.update("delete from product_version");
    jdbc.update("delete from product");
    jdbc.update("delete from user_role");
    jdbc.update("delete from app_user");
    jdbc.update("delete from organization");
    jdbc.update("insert into organization(id,name,code) values (450,'智鹿科技','ZHILU-STRUCTURE')");
    jdbc.update("insert into organization(id,name,code) values (451,'其他组织','OTHER-STRUCTURE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (450,450,'product','产品经理','ACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (451,451,'outsider','其他用户','ACTIVE')");
  }

  @Test
  void createsOrderedModulesAndFiltersFeaturesByModule() throws Exception {
    long productId = product(450L, "ERP", "ERP");
    long laterRoot = createModule(productId, null, "LATER", "后序根", 20);
    long firstRoot = createModule(productId, null, "FIRST", "先序根", 10);
    long child = createModule(productId, firstRoot, "CHILD", "子模块", 5);

    mvc.perform(get("/api/v1/products/{productId}/modules", productId).with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(firstRoot))
        .andExpect(jsonPath("$[1].id").value(laterRoot))
        .andExpect(jsonPath("$[2].id").value(child))
        .andExpect(jsonPath("$[2].parentId").value(firstRoot));

    createFeature(productId, laterRoot, "REPORT", "报表");
    long receivable = createFeature(productId, child, "AR", "应收");
    mvc.perform(get("/api/v1/products/{productId}/features", productId)
            .param("moduleId", String.valueOf(child)).with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(receivable))
        .andExpect(jsonPath("$[0].moduleId").value(child));

    assertEquals(Integer.valueOf(5), jdbc.queryForObject(
        "select count(*) from audit_log where organization_id=450 and resource_type in "
            + "('PRODUCT_MODULE','PRODUCT_FEATURE')", Integer.class));
  }

  @Test
  void rejectsFourthLevelAndCycles() throws Exception {
    long productId = product(450L, "ERP", "ERP");
    long root = createModule(productId, null, "ROOT", "Root", 0);
    long levelTwo = createModule(productId, root, "L2", "Level 2", 0);
    long levelThree = createModule(productId, levelTwo, "L3", "Level 3", 0);

    mvc.perform(post("/api/v1/products/{productId}/modules", productId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(moduleJson(levelThree, "L4", "Level 4", "PLANNING", 0, 0)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("模块树最多三级且不能成环"));

    mvc.perform(put("/api/v1/products/{productId}/modules/{moduleId}", productId, root)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(moduleJson(levelThree, "ROOT", "Root", "PLANNING", 0, 0)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("模块树最多三级且不能成环"));
  }

  @Test
  void rejectsMoveWhenDescendantSubtreeWouldExceedThreeLevels() throws Exception {
    long productId = product(450L, "ERP", "ERP");
    long branch = createModule(productId, null, "BRANCH", "Branch", 0);
    createModule(productId, branch, "LEAF", "Leaf", 0);
    long targetRoot = createModule(productId, null, "TARGET", "Target", 0);
    long targetChild = createModule(productId, targetRoot, "TARGET-CHILD", "Target Child", 0);

    mvc.perform(put("/api/v1/products/{productId}/modules/{moduleId}", productId, branch)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(moduleJson(targetChild, "BRANCH", "Branch", "PLANNING", 0, 0)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("模块树最多三级且不能成环"));
  }

  @Test
  void rejectsConcurrentMovesThatWouldCreateCycle() throws Exception {
    long productId = product(450L, "ERP", "ERP");
    long firstId = createModule(productId, null, "FIRST", "First", 0);
    long secondId = createModule(productId, null, "SECOND", "Second", 0);
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    CountDownLatch firstModuleLocked = new CountDownLatch(1);
    CountDownLatch releaseFirstModule = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(3);
    Future<?> blocker = executor.submit(() -> transaction.execute(status -> {
      jdbc.queryForObject("select id from product_module where id=? for update",
          Long.class, firstId);
      firstModuleLocked.countDown();
      await(releaseFirstModule);
      return null;
    }));

    try {
      assertTrue(firstModuleLocked.await(5, TimeUnit.SECONDS));
      Future<RuntimeException> firstMove = executor.submit(() -> moveFailure(
          productId, firstId, secondId, "FIRST", "First"));
      assertThrows(TimeoutException.class, () -> firstMove.get(250, TimeUnit.MILLISECONDS));

      Future<RuntimeException> secondMove = executor.submit(() -> moveFailure(
          productId, secondId, firstId, "SECOND", "Second"));
      assertThrows(TimeoutException.class, () -> secondMove.get(250, TimeUnit.MILLISECONDS));

      releaseFirstModule.countDown();
      blocker.get(5, TimeUnit.SECONDS);
      assertNull(firstMove.get(5, TimeUnit.SECONDS));
      assertTrue(secondMove.get(5, TimeUnit.SECONDS) instanceof IllegalArgumentException);
      assertEquals(Long.valueOf(secondId), jdbc.queryForObject(
          "select parent_id from product_module where id=?", Long.class, firstId));
      assertNull(jdbc.queryForObject(
          "select parent_id from product_module where id=?", Long.class, secondId));
    } finally {
      releaseFirstModule.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void rejectsCrossProductParentsAndFeatureModules() throws Exception {
    long firstProduct = product(450L, "ERP", "ERP");
    long secondProduct = product(450L, "CRM", "CRM");
    long foreignModule = createModule(secondProduct, null, "FOREIGN", "Foreign", 0);

    mvc.perform(post("/api/v1/products/{productId}/modules", firstProduct)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(moduleJson(foreignModule, "CHILD", "Child", "PLANNING", 0, 0)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("父模块不属于当前产品"));

    mvc.perform(post("/api/v1/products/{productId}/features", firstProduct)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(featureJson(foreignModule, "BROKEN", "Broken", "PLANNING", 0)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("所属模块不属于当前产品"));
  }

  @Test
  void reportsDuplicateModuleAndFeatureCodesAsConflicts() throws Exception {
    long productId = product(450L, "ERP", "ERP");
    long moduleId = createModule(productId, null, "FIN", "Finance", 0);

    mvc.perform(post("/api/v1/products/{productId}/modules", productId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(moduleJson(null, "FIN", "Duplicate", "PLANNING", 0, 0)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("模块编码已存在"));

    createFeature(productId, moduleId, "AR", "Receivable");
    mvc.perform(post("/api/v1/products/{productId}/features", productId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(featureJson(moduleId, "AR", "Duplicate", "PLANNING", 0)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("功能编码已存在"));
  }

  @Test
  void archivedProductsRemainReadableButRejectStructureWrites() throws Exception {
    long productId = product(450L, "ERP", "ERP");
    long moduleId = createModule(productId, null, "FIN", "Finance", 0);
    long featureId = createFeature(productId, moduleId, "AR", "Receivable");
    jdbc.update("update product set status='ARCHIVED' where id=?", productId);

    mvc.perform(get("/api/v1/products/{productId}/modules", productId).with(reader()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get("/api/v1/products/{productId}/features", productId).with(reader()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    mvc.perform(post("/api/v1/products/{productId}/modules", productId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(moduleJson(null, "NEW", "New", "PLANNING", 0, 0)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("产品已归档，不能修改模块或功能"));
    mvc.perform(put("/api/v1/products/{productId}/features/{featureId}", productId, featureId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(featureJson(moduleId, "AR", "Changed", "PLANNING", 0)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("产品已归档，不能修改模块或功能"));
  }

  @Test
  void rejectsFeatureCreationThatWaitsBehindConcurrentArchive() throws Exception {
    long productId = product(450L, "ERP", "ERP");
    long moduleId = createModule(productId, null, "FIN", "Finance", 0);
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    CountDownLatch archived = new CountDownLatch(1);
    CountDownLatch allowArchiveCommit = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    Future<?> archive = executor.submit(() -> transaction.execute(status -> {
      jdbc.update("update product set status='ARCHIVED' where id=?", productId);
      archived.countDown();
      await(allowArchiveCommit);
      return null;
    }));

    try {
      assertTrue(archived.await(5, TimeUnit.SECONDS));
      Future<RuntimeException> creation = executor.submit(() -> {
        try {
          structures.saveFeature(450L, 450L, productId, null, moduleId, null,
              "AR", "Receivable", null, "PLANNING", 0L);
          return null;
        } catch (RuntimeException exception) {
          return exception;
        }
      });
      assertThrows(TimeoutException.class, () -> creation.get(250, TimeUnit.MILLISECONDS));

      allowArchiveCommit.countDown();
      archive.get(5, TimeUnit.SECONDS);
      assertTrue(creation.get(5, TimeUnit.SECONDS) instanceof ConflictException);
      assertEquals(Integer.valueOf(0), jdbc.queryForObject(
          "select count(*) from product_feature where product_id=?", Integer.class, productId));
    } finally {
      allowArchiveCommit.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void enforcesOptimisticLockingForModulesAndFeatures() throws Exception {
    long productId = product(450L, "ERP", "ERP");
    long moduleId = createModule(productId, null, "FIN", "Finance", 0);
    long featureId = createFeature(productId, moduleId, "AR", "Receivable");

    mvc.perform(put("/api/v1/products/{productId}/modules/{moduleId}", productId, moduleId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(moduleJson(null, "FIN", "Finance 2", "ACTIVE", 0, 0)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    mvc.perform(put("/api/v1/products/{productId}/modules/{moduleId}", productId, moduleId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(moduleJson(null, "FIN", "Stale", "ACTIVE", 0, 0)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("模块已被更新，请刷新后重试"));

    mvc.perform(put("/api/v1/products/{productId}/features/{featureId}", productId, featureId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(featureJson(moduleId, "AR", "Receivable 2", "ACTIVE", 0)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    mvc.perform(put("/api/v1/products/{productId}/features/{featureId}", productId, featureId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(featureJson(moduleId, "AR", "Stale", "ACTIVE", 0)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("功能已被更新，请刷新后重试"));
  }

  @Test
  void enforcesForwardOnlyLifecycleAndLocksDeprecatedRecords() throws Exception {
    long productId = product(450L, "ERP", "ERP");
    long moduleId = createModule(productId, null, "FIN", "Finance", 0);
    long featureId = createFeature(productId, moduleId, "AR", "Receivable");

    updateModule(productId, moduleId, "ACTIVE", 0).andExpect(status().isOk());
    updateModule(productId, moduleId, "PLANNING", 1).andExpect(status().isBadRequest());
    updateModule(productId, moduleId, "DEPRECATED", 1).andExpect(status().isOk());
    updateModule(productId, moduleId, "DEPRECATED", 2)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("模块已废弃，不能修改"));

    updateFeature(productId, featureId, moduleId, "ACTIVE", 0).andExpect(status().isOk());
    updateFeature(productId, featureId, moduleId, "DEPRECATED", 1).andExpect(status().isOk());
    updateFeature(productId, featureId, moduleId, "DEPRECATED", 2)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("功能已废弃，不能修改"));
  }

  @Test
  void startsNewModulesAndFeaturesInPlanning() throws Exception {
    long productId = product(450L, "ERP", "ERP");
    long moduleId = createModule(productId, null, "FIN", "Finance", 0);

    mvc.perform(post("/api/v1/products/{productId}/modules", productId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(moduleJson(null, "ACTIVE", "Active", "ACTIVE", 0, 0)))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/products/{productId}/features", productId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(featureJson(moduleId, "ACTIVE", "Active", "ACTIVE", 0)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsOwnersOutsideTheProductOrganization() throws Exception {
    long productId = product(450L, "ERP", "ERP");
    long moduleId = createModule(productId, null, "FIN", "Finance", 0);

    mvc.perform(post("/api/v1/products/{productId}/modules", productId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"ownerUserId\":451,\"code\":\"BAD\",\"name\":\"Bad\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("负责人不存在或不属于当前组织"));
    mvc.perform(post("/api/v1/products/{productId}/features", productId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"moduleId\":" + moduleId
                + ",\"ownerUserId\":451,\"code\":\"BAD\",\"name\":\"Bad\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("负责人不存在或不属于当前组织"));
  }

  @Test
  void keepsProductStructureOrganizationScoped() throws Exception {
    long productId = product(450L, "ERP", "ERP");
    createModule(productId, null, "FIN", "Finance", 0);

    mvc.perform(get("/api/v1/products/{productId}/modules", productId)
            .with(actor(451L, "product:read")))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/products/{productId}/modules", productId)
            .with(actor(451L, "product:write")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(moduleJson(null, "BAD", "Bad", "PLANNING", 0, 0)))
        .andExpect(status().isNotFound());
  }

  private long product(long organizationId, String code, String name) {
    jdbc.update("insert into product(organization_id,code,name,status) values (?,?,?,'ACTIVE')",
        organizationId, code, name);
    return jdbc.queryForObject("select id from product where organization_id=? and code=?",
        Long.class, organizationId, code);
  }

  private long createModule(long productId, Long parentId, String code, String name, int sortOrder)
      throws Exception {
    String response = mvc.perform(post("/api/v1/products/{productId}/modules", productId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(moduleJson(parentId, code, name, "PLANNING", sortOrder, 0)))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(response).get("id").asLong();
  }

  private long createFeature(long productId, long moduleId, String code, String name)
      throws Exception {
    String response = mvc.perform(post("/api/v1/products/{productId}/features", productId)
            .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(featureJson(moduleId, code, name, "PLANNING", 0)))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(response).get("id").asLong();
  }

  private RuntimeException moveFailure(
      long productId, long moduleId, long parentId, String code, String name) {
    try {
      structures.saveModule(450L, 450L, productId, moduleId, parentId, null,
          code, name, null, "PLANNING", 0, 0L);
      return null;
    } catch (RuntimeException exception) {
      return exception;
    }
  }

  private void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for concurrent structure operation");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for concurrent structure operation",
          interrupted);
    }
  }

  private org.springframework.test.web.servlet.ResultActions updateModule(
      long productId, long moduleId, String status, long version) throws Exception {
    return mvc.perform(put("/api/v1/products/{productId}/modules/{moduleId}", productId, moduleId)
        .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(moduleJson(null, "FIN", "Finance", status, 0, version)));
  }

  private org.springframework.test.web.servlet.ResultActions updateFeature(
      long productId, long featureId, long moduleId, String status, long version) throws Exception {
    return mvc.perform(put("/api/v1/products/{productId}/features/{featureId}", productId, featureId)
        .with(writer()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
        .content(featureJson(moduleId, "AR", "Receivable", status, version)));
  }

  private String moduleJson(Long parentId, String code, String name,
      String status, int sortOrder, long version) throws Exception {
    JsonNode parent = parentId == null ? null : json.getNodeFactory().numberNode(parentId);
    com.fasterxml.jackson.databind.node.ObjectNode value = json.createObjectNode();
    value.set("parentId", parent == null ? json.nullNode() : parent);
    value.put("code", code).put("name", name).put("status", status)
        .put("sortOrder", sortOrder).put("version", version);
    return json.writeValueAsString(value);
  }

  private String featureJson(long moduleId, String code, String name, String status, long version)
      throws Exception {
    return json.writeValueAsString(json.createObjectNode().put("moduleId", moduleId)
        .put("code", code).put("name", name).put("status", status).put("version", version));
  }

  private RequestPostProcessor reader() {
    return actor(450L, "product:read");
  }

  private RequestPostProcessor writer() {
    return actor(450L, "product:write");
  }

  private RequestPostProcessor actor(long organizationId, String permission) {
    CurrentUser principal = new CurrentUser(organizationId, organizationId, "product", "产品经理",
        Collections.singletonList("PRODUCT_MANAGER"), Collections.singletonList(permission));
    return authentication(new UsernamePasswordAuthenticationToken(principal, null,
        Collections.singletonList(new SimpleGrantedAuthority(permission))));
  }
}
