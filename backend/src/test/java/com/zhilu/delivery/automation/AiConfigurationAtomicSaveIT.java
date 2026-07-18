package com.zhilu.delivery.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.NestedServletException;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ai-configuration-atomic;"
        + "MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none",
    "delivery.settings.encryption-key=test-settings-master-key-2026"
})
@AutoConfigureMockMvc
class AiConfigurationAtomicSaveIT {
  private static final long ORGANIZATION_ID = 7600L;
  private static final long MISSING_ACTOR_ID = 7601L;

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private AiClient ai;

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    jdbc.update("delete from audit_log");
    jdbc.update("delete from system_setting");
    jdbc.update("delete from app_user");
    jdbc.update("delete from organization");
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (7600,'原子配置组织','AI-ATOMIC')");
  }

  @Test
  void auditFailureRollsBackEveryAiSettingWrite() throws Exception {
    AtomicBoolean remoteValidationTransactionActive = new AtomicBoolean(true);
    when(ai.completeJson(any(AiConnection.class), anyString(), anyString(), any(JsonNode.class)))
        .thenAnswer(invocation -> {
          remoteValidationTransactionActive.set(
              TransactionSynchronizationManager.isActualTransactionActive());
          return json.readTree("{\"status\":\"ok\"}");
        });

    NestedServletException failure = assertThrows(NestedServletException.class,
        () -> mvc.perform(put("/api/v1/admin/ai-service/config")
                .with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"baseUrl\":\"https://ai.example.com/v1\","
                    + "\"model\":\"qwen-plus\",\"apiKey\":\"new-secret\"}"))
            .andReturn());

    assertTrue(hasCause(failure, DataIntegrityViolationException.class));
    assertFalse(remoteValidationTransactionActive.get());
    assertEquals(0, jdbc.queryForObject(
        "select count(*) from system_setting where organization_id=?",
        Integer.class, ORGANIZATION_ID));
  }

  private RequestPostProcessor admin() {
    CurrentUser principal = new CurrentUser(
        MISSING_ACTOR_ID, ORGANIZATION_ID, "admin", "系统管理员",
        Collections.singletonList("ADMIN"), Collections.singletonList("system:manage"));
    return authentication(new UsernamePasswordAuthenticationToken(principal, null,
        Arrays.asList(new SimpleGrantedAuthority("system:manage"))));
  }

  private boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (type.isInstance(current)) return true;
    }
    return false;
  }
}
