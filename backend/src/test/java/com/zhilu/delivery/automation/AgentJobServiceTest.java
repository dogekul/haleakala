package com.zhilu.delivery.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zhilu.delivery.common.error.ConflictException;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:agent-job;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none"
})
class AgentJobServiceTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AgentJobService jobs;
  @MockBean private AgentGateway gateway;

  @BeforeEach
  void seed() {
    jdbc.update("delete from audit_log");
    jdbc.update("delete from callback_receipt");
    jdbc.update("delete from agent_attempt");
    jdbc.update("delete from agent_job");
    jdbc.update("delete from project_activity");
    jdbc.update("delete from project_artifact");
    jdbc.update("delete from template_instance");
    jdbc.update("delete from milestone");
    jdbc.update("delete from project_risk");
    jdbc.update("delete from stage_instance");
    jdbc.update("delete from project_member");
    jdbc.update("delete from delivery_project");
    jdbc.update("delete from product_version");
    jdbc.update("delete from product");
    jdbc.update("delete from app_user");
    jdbc.update("delete from organization");
    jdbc.update("insert into organization(id,name,code) values (700,'智鹿科技','ZHILU-AGENT')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values (700,700,'agent-user','交付工程师','ACTIVE')");
    jdbc.update("insert into product(id,code,name,status) values (700,'AGENT','Agent 产品','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (700,700,'V1','ACTIVE')");
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,product_id,product_version_id,manager_user_id,created_by) values (700,700,'PRJ-700','Agent 项目','客户',700,700,700,700)");
    jdbc.update("insert into project_member(project_id,user_id,project_role) values (700,700,'ENGINEER')");
  }

  @Test
  void duplicateIdempotencyKeyReturnsSameJobAndTerminalStateCannotRegress() {
    org.mockito.Mockito.when(gateway.submit(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new AgentSubmission("external-700", "RUNNING"));
    AgentJobView first = jobs.submit(700, "deliver-init", "normal", "same-key", 700);
    AgentJobView duplicate = jobs.submit(700, "deliver-init", "normal", "same-key", 700);
    jobs.accept(new AgentEvent("evt-1", "external-700", "SUCCEEDED", 100, null,
        Collections.<AgentArtifact>emptyList()));

    assertEquals(first.getId(), duplicate.getId());
    assertThrows(ConflictException.class, () -> jobs.accept(new AgentEvent(
        "evt-2", "external-700", "RUNNING", 80, null,
        Collections.<AgentArtifact>emptyList())));
  }

  @Test
  void duplicateCallbackIsAppliedOnce() {
    org.mockito.Mockito.when(gateway.submit(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new AgentSubmission("external-701", "RUNNING"));
    jobs.submit(700, "deliver-require", "normal", "callback-key", 700);
    AgentEvent event = new AgentEvent("evt-once", "external-701", "SUCCEEDED", 100,
        null, Collections.<AgentArtifact>emptyList());
    jobs.accept(event);
    jobs.accept(event);

    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from callback_receipt where event_id='evt-once'", Integer.class));
  }
}
