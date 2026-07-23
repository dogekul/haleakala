package com.zhilu.delivery.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.operation.CustomerOperationService;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:project-lifecycle;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
class ProjectLifecycleTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProjectService projects;
  @Autowired private CustomerOperationService operations;

  @BeforeEach
  void seedFoundation() {
    jdbc.update("delete from customer_operation");
    jdbc.update("delete from opportunity_artifact");
    jdbc.update("delete from opportunity_activity");
    jdbc.update("delete from sales_opportunity");
    jdbc.update("delete from project_activity");
    jdbc.update("delete from project_artifact");
    jdbc.update("delete from template_instance");
    jdbc.update("delete from milestone");
    jdbc.update("delete from project_risk");
    jdbc.update("delete from stage_instance");
    jdbc.update("delete from project_member");
    jdbc.update("delete from delivery_project");
    jdbc.update("delete from document_job");
    jdbc.update("delete from customer");
    jdbc.update("delete from product_version");
    jdbc.update("delete from product");
    jdbc.update("delete from app_user");
    jdbc.update("delete from organization");
    jdbc.update("insert into organization(id,name,code) values (600,'智鹿科技','ZHILU-PROJECT')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (600,600,'manager','交付负责人','ACTIVE')");
    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (600,600,'ERP','智鹿 ERP','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (600,600,'V5.2','RELEASED')");
    jdbc.update("insert into customer(id,organization_id,name,status) "
        + "values (600,600,'华东银行','ACTIVE')");
  }

  @Test
  void projectStartsWithSevenOrderedStages() {
    ProjectView project = projects.create(command());

    assertEquals(String.valueOf(project.getId()), project.getCode());
    assertEquals(Arrays.asList("启动", "需求采集", "二开实施", "上线切换", "试运行与移交", "标准化评估", "项目收尾"),
        project.getStages().stream().map(StageView::getName).collect(Collectors.toList()));
    assertEquals("START", project.getCurrentStage());
    assertEquals("ACTIVE", project.getStatus());
    assertEquals("PENDING", project.getDocumentSpaceStatus());
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from document_job where organization_id=600 "
            + "and job_type='PROJECT_INIT' and business_key=?",
        Integer.class, "PROJECT:" + project.getId()));
  }

  @Test
  void reportsConflictWhenGeneratedProjectCodeMatchesALegacyNumericCode() {
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_id,"
            + "customer_name,product_id,product_version_id,manager_user_id,created_by) "
            + "values (900000,600,'900001','历史数字编码项目',600,'华东银行',600,600,600,600)");
    jdbc.update("alter table delivery_project alter column id restart with 900001");

    ConflictException conflict = assertThrows(
        ConflictException.class, () -> projects.create(command()));

    assertEquals("项目编号生成冲突", conflict.getMessage());
  }

  @Test
  void persistedGateModeControlsWhetherBlockingGateStopsAdvance() {
    ProjectView project = projects.create(command());
    projects.setGate(project.getId(), "START", "BLOCKING", "启动检查单未完成", manager());

    assertThrows(ConflictException.class, () -> projects.advanceStage(
        project.getId(), DeliveryStage.REQUIREMENT, manager()));
    jdbc.update("update delivery_project set gate_mode='WARNING' where id=?", project.getId());
    ProjectView advanced = projects.advanceStage(
        project.getId(), DeliveryStage.REQUIREMENT, manager());

    assertEquals("REQUIREMENT", advanced.getCurrentStage());
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from project_activity where project_id=? and action='STAGE_ADVANCED_WITH_WARNING'",
        Integer.class, project.getId()));
  }

  @Test
  void rejectsProjectsUnlessProductAndVersionAreBindableInTheirOrganization() {
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (601,600,'V5.3','PLANNING')");
    IllegalArgumentException planningVersion = assertThrows(
        IllegalArgumentException.class, () -> projects.create(command(600, 601)));
    assertEquals("产品或版本不可用于新项目", planningVersion.getMessage());

    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (601,600,'CRM','CRM','PLANNING')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (602,601,'V1','RELEASED')");
    assertThrows(IllegalArgumentException.class,
        () -> projects.create(command(601, 602)));

    assertThrows(IllegalArgumentException.class,
        () -> projects.create(new CreateProjectCommand(601, "跨组织项目", 600,
            600, 600, 600, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), "BLOCK")));
  }

  @Test
  void closedProjectCannotBeReopenedAndUnknownStatusIsRejected() {
    ProjectView project = projects.create(command());
    ProjectView editable = project;
    assertThrows(ConflictException.class, () -> projects.updateSettings(
        editable.getId(), editable.getName(), "CLOSING", editable.getRiskLevel(),
        editable.getGateMode(), editable.getPlannedEndDate(), editable.getVersion(), manager()));
    for (DeliveryStage stage : DeliveryStage.values()) {
      if (stage != DeliveryStage.START) {
        project = projects.advanceStage(project.getId(), stage, manager());
      }
    }
    assertEquals("CLOSING", project.getStatus());
    ProjectView closed = projects.completeProject(project.getId(), manager());

    assertThrows(ConflictException.class, () -> projects.updateSettings(closed.getId(),
        closed.getName(), "ACTIVE", closed.getRiskLevel(), closed.getGateMode(),
        closed.getPlannedEndDate(), closed.getVersion(), manager()));
    assertThrows(IllegalArgumentException.class, () -> projects.updateSettings(closed.getId(),
        closed.getName(), "HACKED", closed.getRiskLevel(), closed.getGateMode(),
        closed.getPlannedEndDate(), closed.getVersion(), manager()));
  }

  @Test
  void closingAProjectFromAWonOpportunityCreatesExactlyOneOperation() {
    ProjectView project = projects.create(command());
    jdbc.update("insert into sales_opportunity(organization_id,customer_id,customer_name_snapshot,"
            + "title,amount,stage,status,project_id,operation_owner_user_id,created_by) "
            + "values (600,600,'华东银行','核心系统升级',100,'CONTRACT','WON',?,600,600)",
        project.getId());

    for (DeliveryStage stage : DeliveryStage.values()) {
      if (stage != DeliveryStage.START) {
        project = projects.advanceStage(project.getId(), stage, manager());
      }
    }

    assertEquals("CLOSE", project.getCurrentStage());
    operations.ensureForClosedProject(600, project.getId(), 600);
    operations.ensureForClosedProject(600, project.getId(), 600);
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from customer_operation where project_id=? and stage='MAINTENANCE' "
            + "and status='OPEN'", Integer.class, project.getId()));
    assertEquals("华东银行核心系统交付", jdbc.queryForObject(
        "select title from customer_operation where project_id=?", String.class, project.getId()));
  }

  @Test
  void closingAProjectWithoutAnOpportunityDoesNotCreateAnOperation() {
    ProjectView project = projects.create(command());
    for (DeliveryStage stage : DeliveryStage.values()) {
      if (stage != DeliveryStage.START) {
        project = projects.advanceStage(project.getId(), stage, manager());
      }
    }

    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from customer_operation where project_id=?",
        Integer.class, project.getId()));
  }

  private CreateProjectCommand command() {
    return command(600, 600);
  }

  private CreateProjectCommand command(long productId, long versionId) {
    return new CreateProjectCommand(600, "华东银行核心系统交付", 600,
        productId, versionId, 600, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), "BLOCK");
  }

  private CurrentUser manager() {
    return new CurrentUser(600L, 600L, "manager", "交付负责人",
        Collections.singletonList("DELIVERY_MANAGER"),
        Collections.singletonList("project:write"));
  }
}
