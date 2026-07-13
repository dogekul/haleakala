package com.zhilu.delivery.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:knowledge;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none"
})
class KnowledgeServiceTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private KnowledgeService knowledge;
  private CurrentUser user;

  @BeforeEach void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[]{"training_material","code_snippet","knowledge_item","file_version","file_object","product_version","product","app_user","organization"}) jdbc.update("delete from " + table);
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (1100,'智鹿','ZHILU-KB')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values (1100,1100,'expert','方案专家','ACTIVE')");
    jdbc.update("insert into product(id,code,name,status) values (1100,'ERP','企业财务','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (1100,1100,'V5','ACTIVE')");
    user = new CurrentUser(1100L,1100L,"expert","方案专家",Collections.<String>emptyList(),Arrays.asList("knowledge:read","knowledge:write"));
  }

  @Test void publishedItemsAreSearchableByKeywordTagAndType() {
    Map<String,Object> item=knowledge.create(user,"CASE","月末关账提速","把月末关账从三天缩短到一天","通过并行核对和异常前置实现","关账,财务",1100L,1100L,"ORGANIZATION",null,null,null,null,null);
    long id=((Number)item.get("id")).longValue();
    assertEquals(0,knowledge.search(user,"关账","CASE","财务",true).size());
    knowledge.publish(id,user);
    assertEquals(1,knowledge.search(user,"关账","CASE","财务",true).size());
    assertEquals("PUBLISHED",knowledge.get(id,user).get("status"));
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
}
