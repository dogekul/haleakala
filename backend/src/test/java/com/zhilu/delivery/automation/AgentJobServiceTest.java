package com.zhilu.delivery.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.document.ProjectDocumentService;
import com.zhilu.delivery.project.DeliveryStage;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:agent-job;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none",
    "delivery.agent.dispatch-initial-delay-ms=3600000",
    "delivery.agent.reconcile-initial-delay-ms=3600000"
})
class AgentJobServiceTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AgentJobService jobs;
  @MockBean private AgentGateway gateway;
  @MockBean private ProjectDocumentService projectDocuments;

  @BeforeEach
  void seed() {
    jdbc.update("delete from audit_log");
    jdbc.update("delete from system_setting");
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
    jdbc.update("insert into product(id,organization_id,code,name,status) values (700,700,'AGENT','Agent 产品','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (700,700,'V1','RELEASED')");
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,product_id,product_version_id,manager_user_id,created_by) values (700,700,'PRJ-700','Agent 项目','客户',700,700,700,700)");
    jdbc.update("insert into project_member(project_id,user_id,project_role) values (700,700,'ENGINEER')");
    when(projectDocuments.agentDocuments(anyLong(), any(DeliveryStage.class)))
        .thenReturn(Collections.<Map<String, Object>>emptyList());
    when(projectDocuments.agentDocumentIds(anyLong(), any(DeliveryStage.class)))
        .thenReturn(Collections.<Long>emptyList());
  }

  @Test
  void deliverTestDispatchesGoLiveDocumentContext() {
    jdbc.update("update delivery_project set current_stage='GO_LIVE' where id=700");
    Map<String, Object> document = document(
        901L, DeliveryStage.GO_LIVE, "功能性测试报告", 7L);
    when(projectDocuments.agentDocuments(700, DeliveryStage.GO_LIVE))
        .thenReturn(Collections.singletonList(document));
    when(gateway.submit(anyString(), any()))
        .thenReturn(new AgentSubmission("external-go-live", "RUNNING"));

    AgentJobView job = jobs.submit(700, "deliver-test", "normal", "go-live-key", 700);
    jobs.dispatchPending();

    ArgumentCaptor<AgentRequest> request = ArgumentCaptor.forClass(AgentRequest.class);
    verify(gateway).submit(eq("platform-job-" + job.getId()), request.capture());
    assertEquals("GO_LIVE", request.getValue().getContext().get("targetStage"));
    assertEquals(Collections.singletonList(document),
        request.getValue().getContext().get("documents"));
  }

  @Test
  void projectDocumentCallbackAppliesDraftAndCompletesJob() {
    Map<String, Object> document = document(
        901L, DeliveryStage.START, "项目立项登记表", 7L);
    when(projectDocuments.agentDocuments(700, DeliveryStage.START))
        .thenReturn(Collections.singletonList(document));
    when(projectDocuments.agentDocumentIds(700, DeliveryStage.START))
        .thenReturn(Collections.singletonList(901L));
    when(projectDocuments.applyAgentDraft(
        700, DeliveryStage.START, 901L, "项目立项登记表", "# 完整正文", 7L))
        .thenReturn(Collections.<String, Object>emptyMap());
    when(gateway.submit(anyString(), any()))
        .thenReturn(new AgentSubmission("external-document", "RUNNING"));
    AgentJobView job = jobs.submit(700, "deliver-init", "normal", "document-key", 700);
    jobs.dispatchPending();

    jobs.accept(new AgentEvent("evt-document", "external-document", "SUCCEEDED", 100, null,
        Collections.singletonList(new AgentArtifact("项目立项登记表", "text/markdown",
            "# 完整正文", "PROJECT_DOCUMENT", 901L, 7L))));

    verify(projectDocuments).applyAgentDraft(
        700, DeliveryStage.START, 901L, "项目立项登记表", "# 完整正文", 7L);
    assertEquals("SUCCEEDED", jobs.get(job.getId()).getStatus());
  }

  @Test
  void missingProjectDocumentArtifactFailsJob() {
    when(projectDocuments.agentDocuments(700, DeliveryStage.START))
        .thenReturn(Collections.singletonList(document(
            901L, DeliveryStage.START, "项目立项登记表", 7L)));
    when(projectDocuments.agentDocumentIds(700, DeliveryStage.START))
        .thenReturn(Collections.singletonList(901L));
    when(gateway.submit(anyString(), any()))
        .thenReturn(new AgentSubmission("external-missing", "RUNNING"));
    AgentJobView job = jobs.submit(700, "deliver-init", "normal", "missing-key", 700);
    jobs.dispatchPending();

    jobs.accept(new AgentEvent("evt-missing", "external-missing", "SUCCEEDED", 100, null,
        Collections.<AgentArtifact>emptyList()));

    assertEquals("FAILED", jobs.get(job.getId()).getStatus());
    verify(projectDocuments, never()).applyAgentDraft(
        anyLong(), any(DeliveryStage.class), anyLong(), anyString(), anyString(), anyLong());
  }

  @Test
  void duplicateProjectDocumentArtifactFailsJobBeforeApplyingDraft() {
    when(projectDocuments.agentDocuments(700, DeliveryStage.START))
        .thenReturn(Collections.singletonList(document(
            901L, DeliveryStage.START, "项目立项登记表", 7L)));
    when(projectDocuments.agentDocumentIds(700, DeliveryStage.START))
        .thenReturn(Collections.singletonList(901L));
    when(gateway.submit(anyString(), any()))
        .thenReturn(new AgentSubmission("external-duplicate", "RUNNING"));
    AgentJobView job = jobs.submit(700, "deliver-init", "normal", "duplicate-key", 700);
    jobs.dispatchPending();
    AgentArtifact first = new AgentArtifact("项目立项登记表", "text/markdown",
        "# 完整正文", "PROJECT_DOCUMENT", 901L, 7L);
    AgentArtifact duplicate = new AgentArtifact("项立项登记表", "text/markdown",
        "# 重复正文", "PROJECT_DOCUMENT", 901L, 7L);

    jobs.accept(new AgentEvent("evt-duplicate", "external-duplicate", "SUCCEEDED", 100, null,
        Arrays.asList(first, duplicate)));

    assertEquals("FAILED", jobs.get(job.getId()).getStatus());
    verify(projectDocuments, never()).applyAgentDraft(
        anyLong(), any(DeliveryStage.class), anyLong(), anyString(), anyString(), anyLong());
  }

  @Test
  void duplicateIdempotencyKeyReturnsSameJobAndTerminalStateCannotRegress() {
    when(gateway.submit(anyString(), any()))
        .thenReturn(new AgentSubmission("external-700", "RUNNING"));
    AgentJobView first = jobs.submit(700, "deliver-init", "normal", "same-key", 700);
    AgentJobView duplicate = jobs.submit(700, "deliver-init", "normal", "same-key", 700);
    verify(gateway, never()).submit(any(), any());
    jobs.dispatchPending();
    jobs.accept(new AgentEvent("evt-1", "external-700", "SUCCEEDED", 100, null,
        Collections.<AgentArtifact>emptyList()));

    assertEquals(first.getId(), duplicate.getId());
    assertThrows(ConflictException.class, () -> jobs.accept(new AgentEvent(
        "evt-2", "external-700", "RUNNING", 80, null,
        Collections.<AgentArtifact>emptyList())));
  }

  @Test
  void duplicateCallbackIsAppliedOnce() {
    when(gateway.submit(anyString(), any()))
        .thenReturn(new AgentSubmission("external-701", "RUNNING"));
    jobs.submit(700, "deliver-require", "normal", "callback-key", 700);
    jobs.dispatchPending();
    AgentEvent event = new AgentEvent("evt-once", "external-701", "SUCCEEDED", 100,
        null, Collections.<AgentArtifact>emptyList());
    jobs.accept(event);
    jobs.accept(event);

    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from callback_receipt where event_id='evt-once'", Integer.class));
  }

  @Test
  void newJobUsesCurrentOrganizationTimeoutSetting() {
    jdbc.update("insert into system_setting(organization_id,setting_key,setting_value) "
        + "values (700,'agent.timeoutMinutes','45')");
    AgentJobView job = jobs.submit(700, "deliver-init", "normal", "timeout-key", 700);
    Map<String, Object> times = jdbc.queryForMap(
        "select created_at,timeout_at from agent_job where id=?", job.getId());
    long minutes = (((Timestamp) times.get("timeout_at")).getTime()
        - ((Timestamp) times.get("created_at")).getTime()) / 60000L;

    assertTrue(minutes >= 44 && minutes <= 45, "timeout should use the 45 minute setting");
  }

  @Test
  void failedDispatchIsRetriedAndUsesStableRemoteIdempotencyKey() {
    when(gateway.submit(anyString(), any()))
        .thenThrow(new IllegalStateException("temporary outage"))
        .thenReturn(new AgentSubmission("external-retry", "QUEUED"));
    AgentJobView queued = jobs.submit(700, "deliver-dev", "normal", "retry-key", 700);

    jobs.dispatchPending();
    jdbc.update("update agent_job set next_dispatch_at=current_timestamp where id=?", queued.getId());
    jobs.dispatchPending();

    AgentJobView dispatched = jobs.get(queued.getId());
    assertEquals("external-retry", dispatched.getExternalJobId());
    assertEquals("QUEUED", dispatched.getStatus());
    verify(gateway, times(2)).submit(eq("platform-job-" + queued.getId()), any());
    assertEquals(Integer.valueOf(2), jdbc.queryForObject(
        "select count(*) from agent_attempt where agent_job_id=?", Integer.class, queued.getId()));
  }

  @Test
  void reconciliationRecoversWhenCallbackWasLost() {
    when(gateway.submit(anyString(), any()))
        .thenReturn(new AgentSubmission("external-poll", "RUNNING"));
    AgentJobView queued = jobs.submit(700, "deliver-close", "normal", "poll-key", 700);
    jobs.dispatchPending();
    when(gateway.status("external-poll")).thenReturn(new AgentEvent(
        null, "external-poll", "SUCCEEDED", 100, null,
        Collections.<AgentArtifact>emptyList()));

    jobs.reconcileActive();

    assertEquals("SUCCEEDED", jobs.get(queued.getId()).getStatus());
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from callback_receipt where agent_job_id=?", Integer.class, queued.getId()));
  }

  private Map<String, Object> document(
      long id, DeliveryStage stage, String title, long revision) {
    Map<String, Object> value = new LinkedHashMap<String, Object>();
    value.put("projectDocumentId", id);
    value.put("stageCode", stage.name());
    value.put("title", title);
    value.put("markdown", "# " + title + "\n\n模版内容");
    value.put("expectedRevision", revision);
    value.put("gateRequired", true);
    return value;
  }
}
