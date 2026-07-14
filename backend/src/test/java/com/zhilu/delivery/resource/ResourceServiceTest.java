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
    jdbc.update("insert into product(id,organization_id,code,name,status) values (1200,1200,'ERP','企业财务','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (1200,1200,'V5','RELEASED')");
    for(int i=0;i<3;i++)jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,product_id,product_version_id,manager_user_id,created_by) values (?,1200,?,?,?,1200,1200,1200,1200)",1200+i,"P-"+i,"项目"+i,"客户");
    manager=new CurrentUser(1200L,1200L,"manager","交付经理",Collections.<String>emptyList(),Arrays.asList("resource:read","resource:write"));
  }

  @Test void nonOverlappingAssignmentsUsePeakLoadWithoutFalseConflict() {
    resources.assign(manager,1201,1200,"实施顾问",LocalDate.of(2026,7,1),LocalDate.of(2026,7,10),60);
    resources.assign(manager,1201,1201,"方案顾问",LocalDate.of(2026,7,11),LocalDate.of(2026,7,20),70);

    List<Map<String,Object>> load=resources.load(manager,LocalDate.of(2026,7,1),LocalDate.of(2026,7,20));

    assertEquals(70,((Number)load.get(0).get("allocationPercent")).intValue());
    assertEquals("BALANCED",load.get(0).get("loadStatus"));
    assertEquals(0,resources.conflicts(manager,LocalDate.of(2026,7,1),LocalDate.of(2026,7,20)).size());
  }

  @Test void overlappingPairCreatesDeterministicConflictWindow() {
    resources.saveProfile(1201,manager,"高级实施顾问","上海",40,"ACTIVE");
    resources.assign(manager,1201,1200,"实施顾问",LocalDate.of(2026,7,1),LocalDate.of(2026,7,31),70);
    resources.assign(manager,1201,1201,"方案顾问",LocalDate.of(2026,7,10),LocalDate.of(2026,7,20),60);
    List<Map<String,Object>> load=resources.load(manager,LocalDate.of(2026,7,10),LocalDate.of(2026,7,20));
    assertEquals(130,((Number)load.get(0).get("allocationPercent")).intValue());
    assertEquals("OVERLOAD",load.get(0).get("loadStatus"));
    List<Map<String,Object>> conflicts=resources.conflicts(manager,LocalDate.of(2026,7,1),LocalDate.of(2026,7,31));
    assertEquals(1,conflicts.size());
    assertEquals(LocalDate.of(2026,7,10),conflicts.get(0).get("startDate"));
    assertEquals(LocalDate.of(2026,7,20),conflicts.get(0).get("endDate"));
    assertEquals(130,((Number)conflicts.get(0).get("peakAllocationPercent")).intValue());
    assertEquals(2,((List<?>)conflicts.get(0).get("assignments")).size());
  }

  @Test void threeFortyPercentAssignmentsCreateOneTripleConflict() {
    resources.assign(manager,1201,1200,"实施顾问",LocalDate.of(2026,7,1),LocalDate.of(2026,7,31),40);
    resources.assign(manager,1201,1201,"方案顾问",LocalDate.of(2026,7,10),LocalDate.of(2026,7,20),40);
    resources.assign(manager,1201,1202,"数据顾问",LocalDate.of(2026,7,15),LocalDate.of(2026,7,25),40);

    List<Map<String,Object>> load=resources.load(manager,LocalDate.of(2026,7,1),LocalDate.of(2026,7,31));
    List<Map<String,Object>> conflicts=resources.conflicts(manager,LocalDate.of(2026,7,1),LocalDate.of(2026,7,31));

    assertEquals(120,((Number)load.get(0).get("allocationPercent")).intValue());
    assertEquals(1,conflicts.size());
    assertEquals(LocalDate.of(2026,7,15),conflicts.get(0).get("startDate"));
    assertEquals(LocalDate.of(2026,7,20),conflicts.get(0).get("endDate"));
    assertEquals(120,((Number)conflicts.get(0).get("peakAllocationPercent")).intValue());
    List<?> assignments=(List<?>)conflicts.get(0).get("assignments");
    assertEquals(3,assignments.size());
    assertEquals(Arrays.asList("P-0","P-1","P-2"),Arrays.asList(
        ((Map<?,?>)assignments.get(0)).get("projectCode"),
        ((Map<?,?>)assignments.get(1)).get("projectCode"),
        ((Map<?,?>)assignments.get(2)).get("projectCode")));
  }

  @Test void assignmentUpdateCanChangePersonAndProjectWithinOrganization() {
    Map<String,Object> assignment=resources.assign(manager,1201,1200,"实施顾问",LocalDate.of(2026,7,1),LocalDate.of(2026,7,31),70);

    Map<String,Object> updated=resources.updateAssignment(((Number)assignment.get("id")).longValue(),manager,1200,1201,"项目经理",LocalDate.of(2026,7,5),LocalDate.of(2026,7,25),80,"ACTIVE",((Number)assignment.get("version")).longValue());

    assertEquals(1200L,((Number)updated.get("userId")).longValue());
    assertEquals(1201L,((Number)updated.get("projectId")).longValue());
    assertEquals(80,((Number)updated.get("allocationPercent")).intValue());
    assertThrows(NotFoundException.class,()->resources.updateAssignment(((Number)assignment.get("id")).longValue(),manager,1299,1201,"项目经理",LocalDate.of(2026,7,5),LocalDate.of(2026,7,25),80,"ACTIVE",1));
  }

  @Test void skillsAreStructuredAndCrossOrganizationAssignmentIsRejected() {
    long skill=((Number)resources.createSkill(manager,"DB-MYSQL","MySQL","TECHNICAL").get("id")).longValue();
    resources.saveSkill(1201,skill,4,true,36,manager);
    assertEquals("MySQL",((List<?>)resources.team(manager,null).get(1).get("skills")).isEmpty()?"":((Map<?,?>)((List<?>)resources.team(manager,null).get(1).get("skills")).get(0)).get("name"));
    assertThrows(NotFoundException.class,()->resources.assign(manager,1299,1200,"顾问",LocalDate.now(),LocalDate.now().plusDays(2),50));
  }
}
