package com.zhilu.delivery.requirement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

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

  @BeforeEach void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[]{"classification_decision","classification_suggestion","requirement_item","project_member","delivery_project","product_version","product","app_user","organization"}) jdbc.update("delete from " + table);
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (910,'智鹿','ZHILU-REQ-API')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values (910,910,'engineer','工程师','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) values (910,910,'CRM','CRM','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (910,910,'V1','RELEASED')");
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,product_id,product_version_id,manager_user_id,created_by) values (910,910,'PRJ-910','CRM 交付','客户',910,910,910,910)");
    jdbc.update("insert into project_member(project_id,user_id,project_role) values (910,910,'ENGINEER')");
    requirements.create(910,"客户主数据校验规则配置","支持按客户类型配置不同校验规则","访谈","P1",910);
    user = new CurrentUser(910L,910L,"engineer","工程师",Collections.singletonList("DELIVERY_ENGINEER"),Arrays.asList("requirement:read","requirement:write"));
  }

  @Test void listsVisibleRequirementsAndFunnel() throws Exception {
    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
        user, null, Collections.singletonList(new SimpleGrantedAuthority("requirement:read")));
    mvc.perform(get("/api/v1/requirements").with(authentication(authentication))).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("客户主数据校验规则配置"));
    mvc.perform(get("/api/v1/requirements/funnel").with(authentication(authentication))).andExpect(status().isOk())
        .andExpect(jsonPath("$.L0").value(0));
  }
}
