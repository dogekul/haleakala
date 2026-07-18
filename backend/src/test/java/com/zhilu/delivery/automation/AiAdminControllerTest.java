package com.zhilu.delivery.automation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.iam.service.IamService;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AiAdminController.class)
class AiAdminControllerTest {
  private static final String CONFIGURATION = "{\"baseUrl\":\"https://ai.example.com/v1\","
      + "\"model\":\"qwen-plus\",\"apiKey\":\"new-secret\"}";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @MockBean private AiConfigurationService configurations;
  @MockBean private AiClient ai;
  @MockBean private AuditService audit;
  @MockBean private IamService iam;
  @MockBean private JpaMetamodelMappingContext jpaMetamodel;

  @Test
  void readsMaskedConfiguration() throws Exception {
    Map<String, Object> view = new LinkedHashMap<String, Object>();
    view.put("baseUrl", "https://ai.example.com/v1");
    view.put("model", "qwen-plus");
    view.put("apiKeyConfigured", true);
    view.put("source", "ORGANIZATION");
    when(configurations.view(7300L)).thenReturn(view);

    mvc.perform(get("/api/v1/admin/ai-service/config").with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.apiKeyConfigured").value(true))
        .andExpect(jsonPath("$.apiKey").doesNotExist());
  }

  @Test
  void testsDraftWithoutSaving() throws Exception {
    AiConnection connection = connection();
    AiConfigurationDraft draft = new AiConfigurationDraft(connection, true);
    when(configurations.draft(7300L, "https://ai.example.com/v1", "qwen-plus", "new-secret"))
        .thenReturn(draft);
    when(ai.completeJson(eq(connection), anyString(), anyString(), any(JsonNode.class)))
        .thenReturn(json.readTree("{\"status\":\"ok\"}"));

    mvc.perform(post("/api/v1/admin/ai-service/config/test").with(admin()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(CONFIGURATION))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("READY"))
        .andExpect(jsonPath("$.model").value("qwen-plus"));

    verify(configurations).draft(7300L, "https://ai.example.com/v1", "qwen-plus", "new-secret");
    verify(ai).completeJson(eq(connection), anyString(), anyString(), any(JsonNode.class));
    verify(configurations, never()).saveValidated(any(Long.class), any(AiConfigurationDraft.class));
  }

  @Test
  void validatesSavesAndAuditsWithoutSecretMaterial() throws Exception {
    AiConnection connection = connection();
    AiConfigurationDraft draft = new AiConfigurationDraft(connection, true);
    Map<String, Object> view = new LinkedHashMap<String, Object>();
    view.put("baseUrl", "https://ai.example.com/v1");
    view.put("model", "qwen-plus");
    view.put("apiKeyConfigured", true);
    view.put("source", "ORGANIZATION");
    when(configurations.draft(7300L, "https://ai.example.com/v1", "qwen-plus", "new-secret"))
        .thenReturn(draft);
    when(ai.completeJson(eq(connection), anyString(), anyString(), any(JsonNode.class)))
        .thenReturn(json.readTree("{\"status\":\"ok\"}"));
    when(configurations.saveValidated(7300L, draft)).thenReturn(view);

    mvc.perform(put("/api/v1/admin/ai-service/config").with(admin()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(CONFIGURATION))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.apiKeyConfigured").value(true))
        .andExpect(jsonPath("$.apiKey").doesNotExist());

    verify(ai).completeJson(eq(connection), anyString(), anyString(), any(JsonNode.class));
    verify(configurations).saveValidated(7300L, draft);
    ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
    verify(audit).record(eq(7300L), eq(7300L), eq("UPDATE"), eq("AI_CONFIGURATION"),
        eq("7300"), details.capture());
    org.junit.jupiter.api.Assertions.assertTrue(details.getValue().contains("qwen-plus"));
    org.junit.jupiter.api.Assertions.assertTrue(details.getValue().contains("apiKeyReplaced=true"));
    org.junit.jupiter.api.Assertions.assertFalse(details.getValue().contains("new-secret"));
  }

  @Test
  void rejectsUnknownConfigurationFields() throws Exception {
    mvc.perform(put("/api/v1/admin/ai-service/config").with(admin()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(CONFIGURATION.substring(0, CONFIGURATION.length() - 1)
                + ",\"unexpected\":true}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

    verify(configurations, never()).draft(any(Long.class), anyString(), anyString(), anyString());
  }

  @Test
  void doesNotSaveWhenAiValidationFails() throws Exception {
    AiConnection connection = connection();
    AiConfigurationDraft draft = new AiConfigurationDraft(connection, true);
    when(configurations.draft(7300L, "https://ai.example.com/v1", "qwen-plus", "new-secret"))
        .thenReturn(draft);
    when(ai.completeJson(eq(connection), anyString(), anyString(), any(JsonNode.class)))
        .thenThrow(new AiServiceException(AiServiceException.Type.UNAVAILABLE));

    mvc.perform(put("/api/v1/admin/ai-service/config").with(admin()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(CONFIGURATION))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));

    verify(configurations, never()).saveValidated(any(Long.class), any(AiConfigurationDraft.class));
  }

  private AiConnection connection() {
    return new AiConnection(7300L, "https://ai.example.com/v1", "qwen-plus", "new-secret",
        "ORGANIZATION");
  }

  private RequestPostProcessor admin() {
    CurrentUser principal = new CurrentUser(7300L, 7300L, "admin", "系统管理员",
        Collections.singletonList("ADMIN"), Collections.singletonList("system:manage"));
    return authentication(new UsernamePasswordAuthenticationToken(principal, null,
        Arrays.asList(new SimpleGrantedAuthority("system:manage"))));
  }
}
