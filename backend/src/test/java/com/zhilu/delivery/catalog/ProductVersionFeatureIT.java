package com.zhilu.delivery.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.Collections;
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
