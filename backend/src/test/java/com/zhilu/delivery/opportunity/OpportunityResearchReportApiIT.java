package com.zhilu.delivery.opportunity;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhilu.delivery.document.DocumentView;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    "spring.datasource.url=jdbc:h2:mem:opportunity-research-api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class OpportunityResearchReportApiIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private OpportunityResearchReportService reports;
  private DocumentView document;

  @BeforeEach void stub() {
    jdbc.update("delete from audit_log");
    jdbc.update("delete from app_user where id=700");
    jdbc.update("delete from organization where id=700");
    jdbc.update("insert into organization(id,name,code) values (700,'API 组织','REPORT-API')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (700,700,'crm','CRM','ACTIVE')");
    document = new DocumentView(919, "需求调研报告", "# 需求调研", 4,
        Instant.parse("2026-07-17T06:00:00Z"), "READY", null,
        "http://outline/doc/report");
    when(reports.prepare(700, 12, 0)).thenReturn(
        new OpportunityResearchReportService.PreparedReport(document, 88, 6));
    when(reports.read(700, 12)).thenReturn(document);
    when(reports.saveDraft(anyLong(), anyLong(), anyString(), anyString(), anyLong()))
        .thenReturn(document);
    Map<String,Object> advanced = new LinkedHashMap<String,Object>();
    advanced.put("id", 12L);
    advanced.put("stage", "OPPORTUNITY");
    when(reports.submit(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyString(),
        anyLong())).thenReturn(advanced);
  }

  @Test void preparesReadsSavesAndSubmitsReport() throws Exception {
    mvc.perform(post("/api/v1/opportunities/12/research-report/prepare")
            .with(actor("crm:write")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceTemplateId").value(88))
        .andExpect(jsonPath("$.sourceTemplateRevision").value(6))
        .andExpect(jsonPath("$.markdown").value("# 需求调研"));

    mvc.perform(get("/api/v1/opportunities/12/research-report").with(actor("crm:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value(4));

    mvc.perform(put("/api/v1/opportunities/12/research-report")
            .with(actor("crm:write")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"需求调研报告\",\"markdown\":\"# 草稿\",\"revision\":4}"))
        .andExpect(status().isOk());

    mvc.perform(post("/api/v1/opportunities/12/research-report/submit")
            .with(actor("crm:write")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"需求调研报告\",\"markdown\":\"# 完成\","
                + "\"revision\":4,\"opportunityVersion\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.opportunity.stage").value("OPPORTUNITY"))
        .andExpect(jsonPath("$.markdown").value("# 需求调研"));
  }

  @Test void exportsAndEnforcesReadWritePermissions() throws Exception {
    mvc.perform(get("/api/v1/opportunities/12/research-report/export?format=md")
            .with(actor("crm:read")))
        .andExpect(status().isOk())
        .andExpect(content().string("# 需求调研"));
    mvc.perform(get("/api/v1/opportunities/12/research-report").with(actor("crm:write")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/opportunities/12/research-report/prepare")
            .with(actor("crm:read")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0}"))
        .andExpect(status().isForbidden());
  }

  @Test void validatesSubmitBody() throws Exception {
    mvc.perform(post("/api/v1/opportunities/12/research-report/submit")
            .with(actor("crm:write")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"\",\"markdown\":\"\",\"revision\":4}"))
        .andExpect(status().isBadRequest());
  }

  private RequestPostProcessor actor(String... permissions) {
    List<String> values = Arrays.asList(permissions);
    CurrentUser user = new CurrentUser(700L, 700L, "crm", "CRM",
        Collections.singletonList("ADMIN"), values);
    return authentication(new UsernamePasswordAuthenticationToken(user, null,
        values.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList())));
  }
}
