package com.zhilu.delivery.standardization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zhilu.delivery.common.error.ConflictException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:standardization;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none"
})
class StandardizationServiceTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private StandardizationService standardization;

  @BeforeEach void seed() {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[]{"flywheel_metric","cost_attribution","standardization_debt","maturity_assessment","product_baseline","custom_dev_task","classification_decision","requirement_item","delivery_project","product_version","product","app_user","organization"}) jdbc.update("delete from " + table);
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values (1000,'智鹿','ZHILU-STD')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values (1000,1000,'product','产品经理','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) values (1000,1000,'ERP','ERP','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) values (1000,1000,'V5','RELEASED')");
    for (int i=0;i<5;i++) {
      long id=1000+i;
      jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,product_id,product_version_id,manager_user_id,created_by) values (?,1000,?,?,?,1000,1000,1000,1000)",id,"P-"+id,"项目"+id,"客户");
      jdbc.update("insert into requirement_item(id,organization_id,project_id,requirement_code,title,description,status,created_by) values (?,1000,?,?,?,?,'CONFIRMED',1000)",id,id,"R-"+id,"批量对账重跑","批量对账重跑与差异定位");
      jdbc.update("insert into classification_decision(requirement_id,confirmed_level,confirmed_by) values (?,'L1',1000)",id);
      jdbc.update("insert into custom_dev_task(requirement_id,project_id,title,extension_point,status,actual_person_days,actual_cost) values (?,?,?,'reconciliation.retry','DONE',10,20000)",id,id,"对账重跑");
    }
  }

  @Test void fiveProjectsCreateOneIdempotentCandidateAndCloseNeedsVerification() {
    List<Map<String,Object>> first=standardization.evaluateDebts(1000,1000);
    List<Map<String,Object>> second=standardization.evaluateDebts(1000,1000);
    assertEquals(1,first.size()); assertEquals(1,second.size());
    assertEquals(1,jdbc.queryForObject("select count(*) from standardization_debt",Integer.class));
    long debt=((Number)first.get(0).get("id")).longValue();
    assertThrows(ConflictException.class,()->standardization.transitionDebt(debt,"CLOSED","直接关闭",1000));
    standardization.transitionDebt(debt,"PENDING",null,1000);
    standardization.transitionDebt(debt,"INCLUDED",null,1000);
    standardization.transitionDebt(debt,"VERIFYING","进入验证",1000);
    assertEquals("CLOSED",standardization.transitionDebt(debt,"CLOSED","五项目回归通过",1000).get("status"));
  }

  @Test void maturityAndCostAreDeterministic() {
    Map<String,Object> assessment=standardization.assess(1000,1000);
    assertEquals(0,((Number)assessment.get("standardCoverage")).intValue());
    assertEquals(50,((Number)assessment.get("maturityScore")).intValue());
    assertEquals(100000.0,((Number)standardization.costs(1000).get("actualCost")).doubleValue(),0.01);
  }
}
