package com.zhilu.delivery.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.time.LocalDate;
import java.util.Arrays;
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
    "spring.datasource.url=jdbc:h2:mem:version-feature;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class ProductVersionFeatureIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProductVersionFeatureService manifests;
  @Autowired private ProductCatalogService catalog;
  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void seedCatalog() {
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
    jdbc.update("insert into organization(id,name,code) values (550,'智鹿科技','ZHILU-MANIFEST')");
    jdbc.update("insert into organization(id,name,code) values (551,'其他组织','OTHER-MANIFEST')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (550,550,'product','产品经理','ACTIVE')");
  }

  @Test
  void replacesAndReadsSameProductManifestWithOptimisticLocking() throws Exception {
    long productId = product(550L, "ERP");
    long versionId = version(productId, "V1");
    long featureId = feature(productId, "FIN", "AR");

    mvc.perform(put("/api/v1/products/{productId}/versions/{versionId}/features",
            productId, versionId).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(manifestJson(0, featureId, "INCLUDED")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.entries[0].featureId").value(featureId))
        .andExpect(jsonPath("$.entries[0].availability").value("INCLUDED"));

    mvc.perform(get("/api/v1/products/{productId}/versions/{versionId}/features",
            productId, versionId).with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries[0].featureId").value(featureId));

    mvc.perform(put("/api/v1/products/{productId}/versions/{versionId}/features",
            productId, versionId).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(manifestJson(0, featureId, "REMOVED")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("版本清单已被更新，请刷新后重试"));

    mvc.perform(get("/api/v1/products/{productId}/versions/{versionId}/features",
            productId, versionId).with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.entries[0].availability").value("INCLUDED"));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from audit_log where organization_id=550 "
            + "and actor_user_id=550 and action='REPLACE_MANIFEST' "
            + "and resource_type='PRODUCT_VERSION' and resource_id=?",
        Integer.class, String.valueOf(versionId)));
  }

  @Test
  void rejectsInvalidEntriesWithoutChangingExistingManifest() throws Exception {
    long productId = product(550L, "ERP");
    long versionId = version(productId, "V1");
    long featureId = feature(productId, "FIN", "AR");
    long foreignProductId = product(550L, "CRM");
    long foreignFeatureId = feature(foreignProductId, "SALES", "LEAD");
    replace(productId, versionId, 0, featureId, "INCLUDED");

    mvc.perform(put("/api/v1/products/{productId}/versions/{versionId}/features",
            productId, versionId).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":1,\"entries\":[{\"featureId\":" + featureId
                + ",\"availability\":\"REMOVED\"},{\"featureId\":" + foreignFeatureId
                + ",\"availability\":\"INCLUDED\"}]}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("产品功能不存在"));

    mvc.perform(put("/api/v1/products/{productId}/versions/{versionId}/features",
            productId, versionId).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(manifestJson(1, featureId, "UNKNOWN")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("功能可用性不受支持"));

    mvc.perform(put("/api/v1/products/{productId}/versions/{versionId}/features",
            productId, versionId).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":1,\"entries\":[{\"featureId\":" + featureId
                + ",\"availability\":\"INCLUDED\"},{\"featureId\":" + featureId
                + ",\"availability\":\"PLANNED\"}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("版本清单不能包含重复功能"));

    mvc.perform(get("/api/v1/products/{productId}/versions/{versionId}/features",
            productId, versionId).with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.entries.length()").value(1))
        .andExpect(jsonPath("$.entries[0].availability").value("INCLUDED"));
  }

  @Test
  void releasesVersionOnlyWithDateAndIncludedFeature() throws Exception {
    long productId = product(550L, "ERP");
    long featureId = feature(productId, "FIN", "AR");
    long emptyVersionId = version(productId, "V1");

    mvc.perform(put("/api/v1/products/{productId}/versions/{versionId}",
            productId, emptyVersionId).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(versionJson("V1", "2026-07-01", 0)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("已发布版本必须包含至少一个已纳入功能"));

    long releasableVersionId = version(productId, "V2");
    replace(productId, releasableVersionId, 0, featureId, "INCLUDED");
    mvc.perform(put("/api/v1/products/{productId}/versions/{versionId}",
            productId, releasableVersionId).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(versionJson("V2", null, 1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("已发布版本必须填写发布日期"));
    mvc.perform(put("/api/v1/products/{productId}/versions/{versionId}",
            productId, releasableVersionId).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(versionJson("V2", "2026-07-01", 1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RELEASED"))
        .andExpect(jsonPath("$.version").value(2));
  }

  @Test
  void appendAdvancesVersionAndIsIdempotent() {
    long productId = product(550L, "ERP");
    long versionId = version(productId, "V1");
    long featureId = feature(productId, "FIN", "AR");

    manifests.appendPlannedFeature(550L, productId, versionId, featureId);

    assertEquals(Long.valueOf(1L), jdbc.queryForObject(
        "select version from product_version where id=?", Long.class, versionId));
    assertThrows(ConflictException.class, () -> manifests.replaceManifest(
        550L, 550L, productId, versionId, 0L,
        Collections.singletonList(
            new ProductVersionFeatureService.ManifestEntry(featureId, "REMOVED"))));

    manifests.appendPlannedFeature(550L, productId, versionId, featureId);

    assertEquals(Long.valueOf(1L), jdbc.queryForObject(
        "select version from product_version where id=?", Long.class, versionId));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from product_version_feature where product_version_id=? "
            + "and product_feature_id=? and availability='PLANNED'",
        Integer.class, versionId, featureId));
  }

  @Test void replaceRejectsNonPlanningVersionAndInactiveProductWithoutAuditOrWrites() {
    long productId = product(550L, "ERP");
    long versionId = version(productId, "V1");
    long featureId = feature(productId, "FIN", "AR");
    jdbc.update("update product_version set status='RELEASED' where id=?", versionId);

    assertThrows(ConflictException.class, () -> manifests.replaceManifest(
        550L, 550L, productId, versionId, 0L, Collections.singletonList(
            new ProductVersionFeatureService.ManifestEntry(featureId, "INCLUDED"))));
    jdbc.update("update product_version set status='PLANNING' where id=?", versionId);
    jdbc.update("update product set status='ARCHIVED' where id=?", productId);
    assertThrows(ConflictException.class, () -> manifests.replaceManifest(
        550L, 550L, productId, versionId, 0L, Collections.singletonList(
            new ProductVersionFeatureService.ManifestEntry(featureId, "INCLUDED"))));

    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from product_version_feature where product_version_id=?",
        Integer.class, versionId));
    assertEquals(Long.valueOf(0), jdbc.queryForObject(
        "select version from product_version where id=?", Long.class, versionId));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from audit_log where action='REPLACE_MANIFEST'", Integer.class));
  }

  @Test void replaceAllowsPlanningActiveAndSunsetProductsButRejectsArchived() {
    int index = 0;
    for (String status : Arrays.asList("PLANNING", "ACTIVE", "SUNSET")) {
      long productId = product(550L, "MANIFEST-" + status);
      long versionId = version(productId, "V1");
      long featureId = feature(productId, "MODULE-" + index, "FEATURE-" + index);
      jdbc.update("update product set status=? where id=?", status, productId);

      manifests.replaceManifest(550L, 550L, productId, versionId, 0L,
          Collections.singletonList(
              new ProductVersionFeatureService.ManifestEntry(featureId, "INCLUDED")));

      assertEquals(Integer.valueOf(1), jdbc.queryForObject(
          "select count(*) from product_version_feature where product_version_id=?",
          Integer.class, versionId));
      index++;
    }

    long archivedProductId = product(550L, "MANIFEST-ARCHIVED");
    long archivedVersionId = version(archivedProductId, "V1");
    long archivedFeatureId = feature(archivedProductId, "ARCHIVED", "FEATURE");
    jdbc.update("update product set status='ARCHIVED' where id=?", archivedProductId);

    assertThrows(ConflictException.class, () -> manifests.replaceManifest(
        550L, 550L, archivedProductId, archivedVersionId, 0L,
        Collections.singletonList(
            new ProductVersionFeatureService.ManifestEntry(archivedFeatureId, "INCLUDED"))));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from product_version_feature where product_version_id=?",
        Integer.class, archivedVersionId));
    assertEquals(Integer.valueOf(3), jdbc.queryForObject(
        "select count(*) from audit_log where action='REPLACE_MANIFEST'", Integer.class));
  }

  @Test void replaceWaitsForConcurrentReleaseThenRejectsWithoutWritesOrAudit() throws Exception {
    long productId = product(550L, "ERP");
    long versionId = version(productId, "V1");
    long includedFeatureId = feature(productId, "FIN", "AR");
    long candidateFeatureId = feature(productId, "SALES", "LEAD");
    jdbc.update("insert into product_version_feature(product_version_id,product_feature_id,"
        + "availability) values (?,?,'INCLUDED')", versionId, includedFeatureId);
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    CountDownLatch released = new CountDownLatch(1);
    CountDownLatch allowCommit = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    Future<?> release = executor.submit(() -> transaction.execute(status -> {
      catalog.updateVersion(550L, productId, versionId,
          LocalDate.of(2026, 7, 1), "RELEASED", 0L);
      released.countDown();
      await(allowCommit);
      return null;
    }));
    try {
      assertTrue(released.await(5, TimeUnit.SECONDS));
      Future<RuntimeException> replace = executor.submit(() -> {
        try {
          manifests.replaceManifest(550L, 550L, productId, versionId, 0L,
              Collections.singletonList(new ProductVersionFeatureService.ManifestEntry(
                  candidateFeatureId, "INCLUDED")));
          return null;
        } catch (RuntimeException exception) {
          return exception;
        }
      });
      assertThrows(TimeoutException.class, () -> replace.get(250, TimeUnit.MILLISECONDS));
      allowCommit.countDown();
      release.get(5, TimeUnit.SECONDS);
      assertTrue(replace.get(5, TimeUnit.SECONDS) instanceof ConflictException);
    } finally {
      allowCommit.countDown();
      executor.shutdownNow();
    }
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from product_version_feature where product_version_id=?",
        Integer.class, versionId));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from audit_log where action='REPLACE_MANIFEST'", Integer.class));
  }

  @Test void replaceWaitsForConcurrentArchiveThenRejectsWithoutWritesOrAudit() throws Exception {
    long productId = product(550L, "ERP");
    long versionId = version(productId, "V1");
    long featureId = feature(productId, "FIN", "AR");
    jdbc.update("update product set status='SUNSET' where id=?", productId);
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    CountDownLatch archived = new CountDownLatch(1);
    CountDownLatch allowCommit = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    Future<?> archive = executor.submit(() -> transaction.execute(status -> {
      catalog.updateProduct(550L, productId, null, "ERP", null, null, "ARCHIVED", 0L);
      archived.countDown();
      await(allowCommit);
      return null;
    }));
    try {
      assertTrue(archived.await(5, TimeUnit.SECONDS));
      Future<RuntimeException> replace = executor.submit(() -> {
        try {
          manifests.replaceManifest(550L, 550L, productId, versionId, 0L,
              Collections.singletonList(new ProductVersionFeatureService.ManifestEntry(
                  featureId, "INCLUDED")));
          return null;
        } catch (RuntimeException exception) {
          return exception;
        }
      });
      assertThrows(TimeoutException.class, () -> replace.get(250, TimeUnit.MILLISECONDS));
      allowCommit.countDown();
      archive.get(5, TimeUnit.SECONDS);
      assertTrue(replace.get(5, TimeUnit.SECONDS) instanceof ConflictException);
    } finally {
      allowCommit.countDown();
      executor.shutdownNow();
    }
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from product_version_feature where product_version_id=?",
        Integer.class, versionId));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from audit_log where action='REPLACE_MANIFEST'", Integer.class));
  }

  @Test
  void rejectsAppendToReleasedVersion() {
    long productId = product(550L, "ERP");
    long versionId = version(productId, "V1");
    long featureId = feature(productId, "FIN", "AR");
    jdbc.update("update product_version set status='RELEASED' where id=?", versionId);

    assertThrows(ConflictException.class,
        () -> manifests.appendPlannedFeature(550L, productId, versionId, featureId));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from product_version_feature where product_version_id=?",
        Integer.class, versionId));
  }

  @Test
  void appendWaitsForConcurrentReleaseAndThenRejectsIt() throws Exception {
    long productId = product(550L, "ERP");
    long versionId = version(productId, "V1");
    long includedFeatureId = feature(productId, "FIN", "AR");
    long candidateFeatureId = feature(productId, "SALES", "LEAD");
    jdbc.update("insert into product_version_feature(product_version_id,product_feature_id,"
        + "availability) values (?,?,'INCLUDED')", versionId, includedFeatureId);
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    CountDownLatch released = new CountDownLatch(1);
    CountDownLatch allowReleaseCommit = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    Future<?> release = executor.submit(() -> transaction.execute(status -> {
      catalog.updateVersion(550L, productId, versionId,
          LocalDate.of(2026, 7, 1), "RELEASED", 0L);
      released.countDown();
      await(allowReleaseCommit);
      return null;
    }));

    try {
      assertTrue(released.await(5, TimeUnit.SECONDS));
      Future<RuntimeException> append = executor.submit(() -> {
        try {
          manifests.appendPlannedFeature(550L, productId, versionId, candidateFeatureId);
          return null;
        } catch (RuntimeException exception) {
          return exception;
        }
      });
      assertThrows(TimeoutException.class, () -> append.get(250, TimeUnit.MILLISECONDS));

      allowReleaseCommit.countDown();
      release.get(5, TimeUnit.SECONDS);
      assertTrue(append.get(5, TimeUnit.SECONDS) instanceof ConflictException);
      assertEquals(Integer.valueOf(0), jdbc.queryForObject(
          "select count(*) from product_version_feature where product_version_id=? "
              + "and product_feature_id=?",
          Integer.class, versionId, candidateFeatureId));
    } finally {
      allowReleaseCommit.countDown();
      executor.shutdownNow();
    }
  }

  private long product(long organizationId, String code) {
    jdbc.update("insert into product(organization_id,code,name,status) values (?,?,?,'ACTIVE')",
        organizationId, code, code);
    return jdbc.queryForObject("select id from product where organization_id=? and code=?",
        Long.class, organizationId, code);
  }

  private long version(long productId, String name) {
    jdbc.update("insert into product_version(product_id,version_name,status) values (?,?,'PLANNING')",
        productId, name);
    return jdbc.queryForObject(
        "select id from product_version where product_id=? and version_name=?",
        Long.class, productId, name);
  }

  private long feature(long productId, String moduleCode, String featureCode) {
    jdbc.update("insert into product_module(product_id,code,name,status) values (?,?,?,'ACTIVE')",
        productId, moduleCode, moduleCode);
    Long moduleId = jdbc.queryForObject(
        "select id from product_module where product_id=? and code=?",
        Long.class, productId, moduleCode);
    jdbc.update("insert into product_feature(product_id,module_id,code,name,status) "
            + "values (?,?,?,?,'ACTIVE')",
        productId, moduleId, featureCode, featureCode);
    return jdbc.queryForObject(
        "select id from product_feature where product_id=? and code=?",
        Long.class, productId, featureCode);
  }

  private void replace(long productId, long versionId, long expectedVersion,
      long featureId, String availability) throws Exception {
    mvc.perform(put("/api/v1/products/{productId}/versions/{versionId}/features",
            productId, versionId).with(writer()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(manifestJson(expectedVersion, featureId, availability)))
        .andExpect(status().isOk());
  }

  private String manifestJson(long version, long featureId, String availability) {
    return "{\"version\":" + version + ",\"entries\":[{\"featureId\":" + featureId
        + ",\"availability\":\"" + availability + "\"}]}";
  }

  private String versionJson(String name, String releaseDate, long version) {
    return "{\"versionName\":\"" + name + "\","
        + (releaseDate == null ? "" : "\"releaseDate\":\"" + releaseDate + "\",")
        + "\"status\":\"RELEASED\",\"version\":" + version + "}";
  }

  private void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for concurrent manifest operation");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for concurrent manifest operation",
          interrupted);
    }
  }

  private RequestPostProcessor reader() {
    return actor("product:read");
  }

  private RequestPostProcessor writer() {
    return actor("product:write");
  }

  private RequestPostProcessor actor(String permission) {
    CurrentUser principal = new CurrentUser(550L, 550L, "product", "产品经理",
        Collections.singletonList("PRODUCT_MANAGER"), Collections.singletonList(permission));
    return authentication(new UsernamePasswordAuthenticationToken(principal, null,
        Collections.singletonList(new SimpleGrantedAuthority(permission))));
  }
}
