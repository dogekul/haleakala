package com.zhilu.delivery.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:resources;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none"
})
class ResourceServiceTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ResourceService resources;
  private CurrentUser manager;

  @BeforeEach void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[]{"resource_assignment","engineer_skill","engineer_profile","skill_catalog","delivery_project","product_version","product","app_user","organization"}) jdbc.update("delete from " + table);
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (1200,'智鹿','ZHILU-RES'),(1201,'其他','OTHER-RES')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values (1200,1200,'manager','交付经理','ACTIVE'),(1201,1200,'engineer','王工程师','ACTIVE'),(1299,1201,'outsider','外部人员','ACTIVE')");
    jdbc.update("insert into product(id,code,name,status) values (1200,'ERP','企业财务','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (1200,1200,'V5','ACTIVE')");
    for(int i=0;i<2;i++)jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,product_id,product_version_id,manager_user_id,created_by) values (?,1200,?,?,?,1200,1200,1200,1200)",1200+i,"P-"+i,"项目"+i,"客户");
    manager=new CurrentUser(1200L,1200L,"manager","交付经理",Collections.<String>emptyList(),Arrays.asList("resource:read","resource:write"));
  }

  @Test void overlappingAssignmentsCreateDeterministicOverload() {
    resources.saveProfile(1201,manager,"高级实施顾问","上海",40,"ACTIVE");
    resources.assign(manager,1201,1200,"实施顾问",LocalDate.of(2026,7,1),LocalDate.of(2026,7,31),70);
    resources.assign(manager,1201,1201,"方案顾问",LocalDate.of(2026,7,10),LocalDate.of(2026,7,20),60);
    List<Map<String,Object>> load=resources.load(manager,LocalDate.of(2026,7,10),LocalDate.of(2026,7,20));
    assertEquals(130,((Number)load.get(0).get("allocationPercent")).intValue());
    assertEquals("OVERLOAD",load.get(0).get("loadStatus"));
    assertEquals(1,resources.conflicts(manager,LocalDate.of(2026,7,1),LocalDate.of(2026,7,31)).size());
  }

  @Test void skillsAreStructuredAndCrossOrganizationAssignmentIsRejected() {
    long skill=((Number)resources.createSkill(manager,"DB-MYSQL","MySQL","TECHNICAL").get("id")).longValue();
    resources.saveSkill(1201,skill,4,true,36,manager);
    assertEquals("MySQL",((List<?>)resources.team(manager,null).get(1).get("skills")).isEmpty()?"":((Map<?,?>)((List<?>)resources.team(manager,null).get(1).get("skills")).get(0)).get("name"));
    assertThrows(NotFoundException.class,()->resources.assign(manager,1299,1200,"顾问",LocalDate.now(),LocalDate.now().plusDays(2),50));
  }
}
