package com.zhilu.delivery.requirement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:requirement-api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class RequirementApiIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RequirementService requirements;
  private CurrentUser user;
  private long requirementId;
  private long uncoveredRequirementId;
  private long firstFeature;
  private long secondFeature;
  private long otherProductFeature;
  private long otherOrganizationFeature;

  @BeforeEach void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[]{"standardization_debt_requirement",
        "requirement_product_feature", "product_feature", "product_module",
        "standardization_debt", "classification_decision", "classification_suggestion",
        "requirement_item", "project_member", "delivery_project", "product_version",
        "product", "app_user", "organization"}) jdbc.update("delete from " + table);
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (910,'智鹿','ZHILU-REQ-API')");
    jdbc.update("insert into organization(id,name,code) values (911,'友商','OTHER-REQ-API')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values (910,910,'engineer','工程师','ACTIVE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values (911,911,'other','友商工程师','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) values (910,910,'CRM','CRM','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) values (911,910,'ERP','ERP','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) values (912,911,'OTHER','友商产品','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (910,910,'V1','RELEASED')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (911,911,'V1','RELEASED')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (912,912,'V1','RELEASED')");
    jdbc.update("insert into product_module(id,product_id,code,name,status) values (910,910,'CUSTOMER','客户管理','ACTIVE')");
    jdbc.update("insert into product_module(id,product_id,code,name,status) values (911,911,'ORDER','订单管理','ACTIVE')");
    jdbc.update("insert into product_module(id,product_id,code,name,status) values (912,912,'OTHER','友商模块','ACTIVE')");
    jdbc.update("insert into product_feature(id,product_id,module_id,code,name,status) values (910,910,910,'VALIDATION','客户校验','ACTIVE')");
    jdbc.update("insert into product_feature(id,product_id,module_id,code,name,status) values (911,910,910,'DUPLICATE','客户查重','ACTIVE')");
    jdbc.update("insert into product_feature(id,product_id,module_id,code,name,status) values (912,911,911,'ORDER_CREATE','订单创建','ACTIVE')");
    jdbc.update("insert into product_feature(id,product_id,module_id,code,name,status) values (913,912,912,'OTHER_FEATURE','友商功能','ACTIVE')");
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,product_id,product_version_id,manager_user_id,created_by) values (910,910,'PRJ-910','CRM 交付','客户',910,910,910,910)");
    jdbc.update("insert into project_member(project_id,user_id,project_role) values (910,910,'ENGINEER')");
    requirementId = ((Number) requirements.create(910,"客户主数据校验规则配置","支持按客户类型配置不同校验规则","访谈","P1",910).get("id")).longValue();
    uncoveredRequirementId = ((Number) requirements.create(910,"客户数据导入","批量导入客户并校验字段","访谈","P2",910).get("id")).longValue();
    jdbc.update("insert into standardization_debt(id,product_version_id,pattern_key,title,occurrence_count,distinct_projects) values (910,910,'CUSTOMER_IMPORT','客户数据导入',1,1)");
    jdbc.update("insert into standardization_debt_requirement(standardization_debt_id,requirement_id) values (910,?)", uncoveredRequirementId);
    firstFeature = 910L;
    secondFeature = 911L;
    otherProductFeature = 912L;
    otherOrganizationFeature = 913L;
    user = new CurrentUser(910L,910L,"engineer","工程师",Collections.singletonList("DELIVERY_ENGINEER"),Arrays.asList("requirement:read","requirement:write"));
  }

  @Test void listsVisibleRequirementsAndFunnel() throws Exception {
    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
        user, null, Collections.singletonList(new SimpleGrantedAuthority("requirement:read")));
    mvc.perform(get("/api/v1/requirements").with(authentication(authentication))).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].productId").value(910));
    mvc.perform(get("/api/v1/requirements/{id}", requirementId)
            .with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productId").value(910));
    mvc.perform(get("/api/v1/requirements/funnel").with(authentication(authentication))).andExpect(status().isOk())
        .andExpect(jsonPath("$.L0").value(0));
  }

  @Test void replacesAndReadsMultipleFeatureCoverageEntries() throws Exception {
    mvc.perform(put("/api/v1/requirements/{id}/product-features", requirementId)
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"entries\":[{\"featureId\":" + firstFeature
                + ",\"coverageType\":\"PARTIAL\"},{\"featureId\":" + secondFeature
                + ",\"coverageType\":\"FULL\"}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementId").value(requirementId))
        .andExpect(jsonPath("$.fullyCovered").value(true))
        .andExpect(jsonPath("$.entries.length()").value(2))
        .andExpect(jsonPath("$.entries[0].featureCode").value("VALIDATION"))
        .andExpect(jsonPath("$.entries[0].featureName").value("客户校验"))
        .andExpect(jsonPath("$.entries[0].moduleName").value("客户管理"))
        .andExpect(jsonPath("$.entries[0].coverageType").value("PARTIAL"))
        .andExpect(jsonPath("$.entries[1].coverageType").value("FULL"));

    mvc.perform(get("/api/v1/requirements/{id}/product-features", requirementId)
            .with(reader()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullyCovered").value(true))
        .andExpect(jsonPath("$.entries.length()").value(2));
  }

  @Test void rejectsDuplicateCrossProductAndCrossOrganizationFeatures() throws Exception {
    mvc.perform(put("/api/v1/requirements/{id}/product-features", requirementId)
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"entries\":[{\"featureId\":" + firstFeature
                + ",\"coverageType\":\"FULL\"},{\"featureId\":" + firstFeature
                + ",\"coverageType\":\"PARTIAL\"}]}"))
        .andExpect(status().isBadRequest());

    mvc.perform(put("/api/v1/requirements/{id}/product-features", requirementId)
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"entries\":[{\"featureId\":" + otherProductFeature
                + ",\"coverageType\":\"FULL\"}]}"))
        .andExpect(status().isNotFound());

    mvc.perform(put("/api/v1/requirements/{id}/product-features", requirementId)
            .with(writer()).with(csrf()).contentType("application/json")
            .content("{\"entries\":[{\"featureId\":" + otherOrganizationFeature
                + ",\"coverageType\":\"FULL\"}]}"))
        .andExpect(status().isNotFound());
  }

  @Test void preservesRequirementProjectDataScopeForCoverage() throws Exception {
    CurrentUser outsider = new CurrentUser(999L, 910L, "outsider", "外部人员",
        Collections.singletonList("DELIVERY_ENGINEER"),
        Arrays.asList("requirement:read", "requirement:write"));
    mvc.perform(get("/api/v1/requirements/{id}/product-features", requirementId)
            .with(actor(outsider, "requirement:read")))
        .andExpect(status().isNotFound());
    mvc.perform(put("/api/v1/requirements/{id}/product-features", requirementId)
            .with(actor(outsider, "requirement:write")).with(csrf())
            .contentType("application/json").content("{\"entries\":[]}"))
        .andExpect(status().isNotFound());
  }

  @Test void groupsProductCoverageAndReturnsDebtLinkedUncoveredRequirements() throws Exception {
    jdbc.update("insert into requirement_product_feature(requirement_id,product_feature_id,coverage_type,source,created_by) values (?,?,'PARTIAL','MANUAL',910)", requirementId, firstFeature);
    jdbc.update("insert into requirement_product_feature(requirement_id,product_feature_id,coverage_type,source,created_by) values (?,?,'FULL','MANUAL',910)", requirementId, secondFeature);

    mvc.perform(get("/api/v1/products/{productId}/coverage", 910)
            .with(actor(user, "product:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productId").value(910))
        .andExpect(jsonPath("$.features.length()").value(2))
        .andExpect(jsonPath("$.features[0].featureId").value(firstFeature))
        .andExpect(jsonPath("$.features[0].fullCount").value(0))
        .andExpect(jsonPath("$.features[0].partialCount").value(1))
        .andExpect(jsonPath("$.features[1].fullCount").value(1))
        .andExpect(jsonPath("$.features[1].partialCount").value(0))
        .andExpect(jsonPath("$.uncoveredRequirements.length()").value(1))
        .andExpect(jsonPath("$.uncoveredRequirements[0].requirementId").value(uncoveredRequirementId))
        .andExpect(jsonPath("$.uncoveredRequirements[0].requirementCode").isNotEmpty())
        .andExpect(jsonPath("$.uncoveredRequirements[0].projectCode").value("PRJ-910"))
        .andExpect(jsonPath("$.uncoveredRequirements[0].debtLinked").value(true));
  }

  @Test void productCoverageRejectsAnotherOrganizationsProduct() throws Exception {
    mvc.perform(get("/api/v1/products/{productId}/coverage", 912)
            .with(actor(user, "product:read")))
        .andExpect(status().isNotFound());
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor reader() {
    return actor(user, "requirement:read");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor writer() {
    return actor(user, "requirement:write");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor actor(
      CurrentUser currentUser, String authority) {
    return authentication(new UsernamePasswordAuthenticationToken(currentUser, null,
        Collections.singletonList(new SimpleGrantedAuthority(authority))));
  }
}
