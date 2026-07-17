package com.zhilu.delivery.opportunity;

import static org.mockito.ArgumentMatchers.anyBoolean;
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
    "spring.datasource.url=jdbc:h2:mem:opportunity-document-api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none"
})
@AutoConfigureMockMvc
class OpportunityStageDocumentApiIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private OpportunityStageDocumentService documents;
  private DocumentView document;

  @BeforeEach
  void stub() {
    jdbc.update("delete from audit_log");
    jdbc.update("delete from app_user where id=720");
    jdbc.update("delete from organization where id=720");
    jdbc.update("insert into organization(id,name,code) values (720,'材料 API','DOC-API')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (720,720,'crm','CRM','ACTIVE')");
    document = new DocumentView(920, "甲方诉求清单", "# 已生成诉求", 4,
        Instant.parse("2026-07-17T06:00:00Z"), "READY", null,
        "http://outline/doc/client-requests");
    OpportunityStageDocumentService.PreparedDocument prepared =
        new OpportunityStageDocumentService.PreparedDocument(document, 89, 7,
            "AI", null, Collections.singletonList("请人工核对"));
    when(documents.prepare(720, 12, "CLIENT_REQUESTS", 2)).thenReturn(prepared);
    when(documents.read(720, 12, "CLIENT_REQUESTS")).thenReturn(document);
    when(documents.saveDraft(anyLong(), anyLong(), anyString(), anyString(), anyString(),
        anyLong())).thenReturn(document);
    when(documents.generate(anyLong(), anyLong(), anyString(), anyLong(), anyBoolean()))
        .thenReturn(prepared);
    Map<String, Object> opportunity = new LinkedHashMap<String, Object>();
    opportunity.put("id", 12L); opportunity.put("stage", "POC");
    when(documents.submit(anyLong(), anyLong(), anyLong(), anyString(), anyLong(), anyString(),
        anyString(), anyLong())).thenReturn(
            new OpportunityStageDocumentService.SubmitResult(document, opportunity));
  }

  @Test
  void preparesReadsSavesGeneratesSubmitsAndExportsGenericDocument() throws Exception {
    mvc.perform(post("/api/v1/opportunities/12/documents/CLIENT_REQUESTS/prepare")
            .with(actor("crm:write")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.generationStatus").value("AI"))
        .andExpect(jsonPath("$.sourceTemplateId").value(89))
        .andExpect(jsonPath("$.warnings[0]").value("请人工核对"));
    mvc.perform(get("/api/v1/opportunities/12/documents/CLIENT_REQUESTS")
            .with(actor("crm:read")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(4));
    mvc.perform(put("/api/v1/opportunities/12/documents/CLIENT_REQUESTS")
            .with(actor("crm:write")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"甲方诉求清单\",\"markdown\":\"# 草稿\",\"revision\":4}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/opportunities/12/documents/CLIENT_REQUESTS/generate")
            .with(actor("crm:write")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"revision\":4,\"confirmOverwrite\":true}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.markdown").value("# 已生成诉求"));
    mvc.perform(post("/api/v1/opportunities/12/documents/CLIENT_REQUESTS/submit")
            .with(actor("crm:write")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"甲方诉求清单\",\"markdown\":\"# 完成\","
                + "\"revision\":4,\"opportunityVersion\":2}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.opportunity.stage").value("POC"));
    mvc.perform(get("/api/v1/opportunities/12/documents/CLIENT_REQUESTS/export?format=md")
            .with(actor("crm:read")))
        .andExpect(status().isOk()).andExpect(content().string("# 已生成诉求"));
  }

  @Test
  void enforcesReadAndWritePermissions() throws Exception {
    mvc.perform(get("/api/v1/opportunities/12/documents/CLIENT_REQUESTS")
            .with(actor("crm:write"))).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/opportunities/12/documents/CLIENT_REQUESTS/prepare")
            .with(actor("crm:read")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":2}"))
        .andExpect(status().isForbidden());
  }

  private RequestPostProcessor actor(String... permissions) {
    List<String> values = Arrays.asList(permissions);
    CurrentUser user = new CurrentUser(720L, 720L, "crm", "CRM",
        Collections.singletonList("ADMIN"), values);
    return authentication(new UsernamePasswordAuthenticationToken(user, null,
        values.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList())));
  }
}
