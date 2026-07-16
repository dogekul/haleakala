package com.zhilu.delivery.knowledge;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhilu.delivery.document.OutlineClient;
import com.zhilu.delivery.document.OutlineDocument;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
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
    "spring.datasource.url=jdbc:h2:mem:knowledge-template-api;MODE=MySQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none",
    "delivery.outline.base-url=http://outline.test",
    "delivery.outline.api-token=ol_api_test",
    "delivery.outline.collection-id=a4296a54-2044-4529-ba86-d598a5322e06"
})
@AutoConfigureMockMvc
class KnowledgeTemplateApiIT {
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @MockBean private OutlineClient outline;
  private final Map<String,OutlineDocument> stored =
      new ConcurrentHashMap<String,OutlineDocument>();
  private final AtomicLong ids = new AtomicLong();

  @BeforeEach
  void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[] {
        "project_document","document_template_config","training_material","code_snippet",
        "knowledge_item","outline_document_link","product_version","product","app_user",
        "organization"
    }) jdbc.update("delete from "+table);
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (5100,'智鹿','KB-TEMPLATE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (5100,5100,'expert','方案专家','ACTIVE')");
    stubOutline();
  }

  @Test
  void createsAndPublishesAnOutlineBackedDocumentTemplate() throws Exception {
    String response=mvc.perform(post("/api/v1/knowledge").with(actor()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"type\":\"TEMPLATE\",\"title\":\"需求规格说明书\","
                + "\"summary\":\"需求阶段必需文档\",\"content\":\"# 需求规格说明书\","
                + "\"visibility\":\"ORGANIZATION\",\"stageCode\":\"REQUIREMENT\","
                + "\"requirement\":\"REQUIRED\",\"enabled\":true}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("TEMPLATE"))
        .andExpect(jsonPath("$.stageCode").value("REQUIREMENT"))
        .andExpect(jsonPath("$.requirement").value("REQUIRED"))
        .andExpect(jsonPath("$.documentStatus").value("READY"))
        .andReturn().getResponse().getContentAsString();
    long id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("id").asLong();

    mvc.perform(post("/api/v1/knowledge/{id}/publish",id).with(actor()).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"))
        .andExpect(jsonPath("$.publishedRevision").isNumber());
  }

  private void stubOutline() {
    stored.clear();
    ids.set(0);
    when(outline.create(anyString(),anyString(),anyString(),nullable(String.class),anyBoolean()))
        .thenAnswer(invocation -> {
          String title=invocation.getArgument(0);
          String id=UUID.nameUUIDFromBytes(
              (title+"-"+ids.incrementAndGet()).getBytes(StandardCharsets.UTF_8)).toString();
          OutlineDocument document=new OutlineDocument(
              id,"a4296a54-2044-4529-ba86-d598a5322e06",invocation.getArgument(3),
              title,invocation.getArgument(1),"/doc/"+id,id.substring(0,8),1,
              Instant.parse("2026-07-16T08:00:00Z"));
          stored.put(id,document);
          return document;
        });
    when(outline.info(anyString())).thenAnswer(
        invocation -> stored.get(invocation.getArgument(0)));
  }

  private RequestPostProcessor actor() {
    CurrentUser principal=new CurrentUser(5100L,5100L,"expert","方案专家",
        Collections.<String>emptyList(),Arrays.asList("knowledge:read","knowledge:write"));
    return authentication(new UsernamePasswordAuthenticationToken(
        principal,null,Arrays.asList(
            new SimpleGrantedAuthority("knowledge:read"),
            new SimpleGrantedAuthority("knowledge:write"))));
  }
}
