package com.zhilu.delivery.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.document.OutlineClient;
import com.zhilu.delivery.document.OutlineConnection;
import com.zhilu.delivery.document.OutlineDocument;
import com.zhilu.delivery.document.OutlineException;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:knowledge;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none",
    "delivery.outline.base-url=http://outline.test",
    "delivery.outline.api-token=ol_api_test",
    "delivery.outline.collection-id=a4296a54-2044-4529-ba86-d598a5322e06"
})
class KnowledgeServiceTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private KnowledgeService knowledge;
  @MockBean private OutlineClient outline;
  private final Map<String, OutlineDocument> outlineDocuments =
      new ConcurrentHashMap<String, OutlineDocument>();
  private final AtomicLong outlineIds = new AtomicLong();
  private CurrentUser user;

  @BeforeEach void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[]{"project_document","document_template_config",
        "training_material","code_snippet","knowledge_item","outline_document_link",
        "file_version","file_object","product_version","product","system_setting","app_user","organization"})
      jdbc.update("delete from " + table);
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (1100,'智鹿','ZHILU-KB')");
    jdbc.update("insert into system_setting(organization_id,setting_key,setting_value,encrypted) "
        + "values (1100,'outline.publicBaseUrl','http://outline.organization',false)");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values (1100,1100,'expert','方案专家','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) values (1100,1100,'ERP','企业财务','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (1100,1100,'V5','RELEASED')");
    user = new CurrentUser(1100L,1100L,"expert","方案专家",Collections.<String>emptyList(),Arrays.asList("knowledge:read","knowledge:write"));
    reset(outline);
    stubOutline();
  }

  @Test void publishedItemsAreSearchableByKeywordTagAndType() {
    Map<String,Object> item=knowledge.create(user,"CASE","月末关账提速","把月末关账从三天缩短到一天","通过并行核对和异常前置实现","关账,财务",1100L,1100L,"ORGANIZATION",null,null,null,null,null);
    long id=((Number)item.get("id")).longValue();
    assertEquals(0,knowledge.search(user,"关账","CASE","财务",true).size());
    knowledge.publish(id,user);
    assertEquals(1,knowledge.search(user,"关账","CASE","财务",true).size());
    assertEquals("PUBLISHED",knowledge.get(id,user).get("status"));
    assertTrue(String.valueOf(item.get("outlineUrl"))
        .startsWith("http://outline.organization/doc/"));
    ArgumentCaptor<OutlineConnection> connection =
        ArgumentCaptor.forClass(OutlineConnection.class);
    verify(outline, atLeastOnce()).info(connection.capture(), anyString());
    assertEquals(1100L, connection.getValue().getOrganizationId());
    assertEquals("ol_api_test", connection.getValue().getApiToken());
  }

  @Test void codeAndTrainingDetailsShareOneKnowledgeLifecycle() {
    Map<String,Object> code=knowledge.create(user,"CODE","对账重试扩展点","幂等重试参考实现","使用业务键去重","对账,扩展点",1100L,1100L,"ORGANIZATION","Java","retryOnce(businessKey);","无外部依赖",null,null);
    Map<String,Object> training=knowledge.create(user,"TRAINING","交付经理训练营","七阶段门禁实操","完成演练后通过测验","交付,培训",1100L,1100L,"ORGANIZATION",null,null,"交付经理",90,null);
    assertEquals("retryOnce(businessKey);",knowledge.get(((Number)code.get("id")).longValue(),user).get("codeText"));
    assertEquals(90,((Number)knowledge.get(((Number)training.get("id")).longValue(),user).get("durationMinutes")).intValue());
    assertThrows(NotFoundException.class,()->knowledge.publish(((Number)training.get("id")).longValue(),new CurrentUser(1199L,1100L,"other","其他",Collections.<String>emptyList(),Arrays.asList("knowledge:read","knowledge:write"))));
  }

  @Test void trainingCreationKeepsUploadedAttachmentMetadata() {
    jdbc.update("insert into file_object(id,organization_id,object_key,original_name,mime_type,size_bytes,checksum_sha256,file_version,created_by) values (1200,1100,'knowledge/training.pdf','培训课件.pdf','application/pdf',2048,?,2,1100)",
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    Map<String,Object> training=knowledge.create(user,"TRAINING","交付训练营","七阶段实操","课程正文","交付",1100L,1100L,"ORGANIZATION",null,null,"交付经理",90,1200L);

    assertEquals(1200L,((Number)training.get("fileObjectId")).longValue());
    assertEquals("培训课件.pdf",training.get("fileOriginalName"));
    assertEquals(2,((Number)training.get("fileVersion")).intValue());
  }

  @Test void creationRejectsProductFromAnotherOrganization() {
    seedOtherOrganizationReferences();

    assertThrows(IllegalArgumentException.class, () -> knowledge.create(user,"CASE",
        "跨组织案例","不应关联其他组织产品","正文","隔离",2100L,2100L,"ORGANIZATION",
        null,null,null,null,null));
  }

  @Test void updateRejectsVersionThatDoesNotBelongToSelectedProduct() {
    jdbc.update("insert into product(id,organization_id,code,name,status) values (1101,1100,'CRM','客户管理','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (1101,1101,'V1','RELEASED')");
    Map<String,Object> item=knowledge.create(user,"CASE","原案例","原摘要","原正文","案例",
        1100L,1100L,"ORGANIZATION",null,null,null,null,null);

    assertThrows(IllegalArgumentException.class, () -> knowledge.update(
        ((Number)item.get("id")).longValue(),user,"CASE","修改案例","修改摘要","修改正文","案例",
        1100L,1101L,"ORGANIZATION",null,null,null,null,null,
        ((Number)item.get("version")).longValue()));
  }

  @Test void trainingUpdateRejectsFileFromAnotherOrganization() {
    seedOtherOrganizationReferences();
    jdbc.update("insert into file_object(id,organization_id,object_key,original_name,mime_type,size_bytes,checksum_sha256,file_version,created_by) values (2200,2100,'other/training.pdf','其他组织课件.pdf','application/pdf',1024,?,1,2100)",
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    Map<String,Object> item=knowledge.create(user,"TRAINING","原培训","原摘要","原正文","培训",
        1100L,1100L,"ORGANIZATION",null,null,"交付经理",60,null);

    assertThrows(IllegalArgumentException.class, () -> knowledge.update(
        ((Number)item.get("id")).longValue(),user,"TRAINING","修改培训","修改摘要","修改正文","培训",
        1100L,1100L,"ORGANIZATION",null,null,"交付经理",60,2200L,
        ((Number)item.get("version")).longValue()));
  }

  @Test void documentTemplatePublishesTheCurrentOutlineRevision() {
    Map<String,Object> template=knowledge.create(user,"TEMPLATE","项目启动检查单",
        "项目启动阶段的必需文档","# 项目启动检查单\n\n请补充项目目标","启动,模版",
        null,null,"ORGANIZATION",null,null,null,null,null,
        "START","REQUIRED",true);
    long id=((Number)template.get("id")).longValue();

    assertEquals("READY",template.get("documentStatus"));
    assertEquals("START",template.get("stageCode"));
    assertEquals("REQUIRED",template.get("requirement"));
    assertEquals(Boolean.TRUE,template.get("enabled"));

    Map<String,Object> published=knowledge.publish(id,user);
    assertEquals("PUBLISHED",published.get("status"));
    assertEquals(published.get("documentRevision"),published.get("publishedRevision"));
    Map<String,Object> snapshot=jdbc.queryForMap(
        "select published_title_snapshot,published_markdown_snapshot "
            + "from document_template_config where knowledge_item_id=?", id);
    assertEquals("项目启动检查单",snapshot.get("published_title_snapshot"));
    assertEquals("# 项目启动检查单\n\n请补充项目目标",
        snapshot.get("published_markdown_snapshot"));
  }

  @Test void opportunityResearchTemplateUsesDedicatedScene() {
    Map<String,Object> template=knowledge.create(user,"TEMPLATE","需求调研报告",
        "商机调研","# {{客户名称}}需求调研报告","商机",
        null,null,"ORGANIZATION",null,null,null,null,null,
        "OPPORTUNITY_RESEARCH","REQUIRED",true);

    assertEquals("OPPORTUNITY_RESEARCH",template.get("stageCode"));
    assertEquals("REQUIRED",template.get("requirement"));
  }

  @Test void failedOutlineUpdateRollsBackLocalMetadataAndFallbackBody() {
    Map<String,Object> item=knowledge.create(user,"CASE","原案例","原摘要","原正文","案例",
        1100L,1100L,"ORGANIZATION",null,null,null,null,null);
    long id=((Number)item.get("id")).longValue();
    assertEquals(1L,((Number)item.get("documentRevision")).longValue());
    String documentId=jdbc.queryForObject(
        "select d.outline_document_id from knowledge_item k "
            + "join outline_document_link d on d.id=k.outline_link_id where k.id=?",
        String.class,id);
    doThrow(new OutlineException(OutlineException.Type.UNAVAILABLE,"Outline is unavailable"))
        .when(outline).update(
            any(OutlineConnection.class), eq(documentId), eq("修改案例"), eq("修改正文"));

    assertThrows(OutlineException.class,()->knowledge.update(
        id,user,"CASE","修改案例","修改摘要","修改正文","案例",
        1100L,1100L,"ORGANIZATION",null,null,null,null,null,
        ((Number)item.get("version")).longValue()));

    Map<String,Object> stored=jdbc.queryForMap(
        "select title,summary,content_text from knowledge_item where id=?",id);
    assertEquals("原案例",stored.get("title"));
    assertEquals("原摘要",stored.get("summary"));
    assertEquals("原正文",stored.get("content_text"));
  }

  @Test void outlineFailureKeepsTheLocalKnowledgeDraftForRetry() {
    doThrow(new OutlineException(
        OutlineException.Type.UNAVAILABLE,"Outline is unavailable"))
        .when(outline).create(any(OutlineConnection.class),anyString(),anyString(),anyString(),
            anyString(),nullable(String.class),anyBoolean());

    Map<String,Object> draft=knowledge.create(user,"CASE","离线草稿","服务不可用时不丢输入",
        "需要稍后同步的正文","离线",null,null,"ORGANIZATION",
        null,null,null,null,null);

    assertEquals("DRAFT",draft.get("status"));
    assertEquals("PENDING",draft.get("documentStatus"));
    assertEquals("需要稍后同步的正文",draft.get("content"));
    assertEquals(Integer.valueOf(1),jdbc.queryForObject(
        "select count(*) from knowledge_item where organization_id=1100 and title='离线草稿'",
        Integer.class));

    reset(outline);
    stubOutline();
    Map<String,Object> recovered=knowledge.retryDocument(
        ((Number)draft.get("id")).longValue(),user);

    assertEquals("READY",recovered.get("documentStatus"));
    assertEquals(null,recovered.get("documentError"));
  }

  private void stubOutline() {
    outlineDocuments.clear();
    outlineIds.set(0);
    when(outline.create(any(OutlineConnection.class),anyString(),anyString(),anyString(),
        anyString(),nullable(String.class),anyBoolean()))
        .thenAnswer(invocation -> {
          String id=invocation.getArgument(1);
          String title=invocation.getArgument(2);
          String text=invocation.getArgument(3);
          String parent=invocation.getArgument(5);
          outlineIds.incrementAndGet();
          OutlineDocument document=document(id,parent,title,text,1);
          outlineDocuments.put(id,document);
          return document;
        });
    when(outline.info(any(OutlineConnection.class),anyString())).thenAnswer(
        invocation -> outlineDocuments.get(invocation.getArgument(1)));
    when(outline.update(
        any(OutlineConnection.class),anyString(),anyString(),anyString()))
        .thenAnswer(invocation -> {
      String id=invocation.getArgument(1);
      OutlineDocument current=outlineDocuments.get(id);
      OutlineDocument updated=document(
          id,current.getParentDocumentId(),invocation.getArgument(2),invocation.getArgument(3),
          current.getRevision()+1);
      outlineDocuments.put(id,updated);
      return updated;
    });
  }

  private OutlineDocument document(
      String id,String parent,String title,String text,long revision) {
    return new OutlineDocument(id,"a4296a54-2044-4529-ba86-d598a5322e06",parent,title,text,
        "/doc/"+id,id.substring(0,8),revision,Instant.parse("2026-07-16T08:00:00Z"));
  }

  private void seedOtherOrganizationReferences() {
    jdbc.update("insert into organization(id,name,code) values (2100,'其他组织','OTHER-KB')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values (2100,2100,'other-expert','其他专家','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) values (2100,2100,'OTHER','其他产品','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (2100,2100,'V1','RELEASED')");
  }
}
