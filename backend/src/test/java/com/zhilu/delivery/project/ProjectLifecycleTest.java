package com.zhilu.delivery.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.iam.service.CurrentUser;
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

  @BeforeEach
  void seedFoundation() {
    jdbc.update("delete from project_activity");
    jdbc.update("delete from project_artifact");
    jdbc.update("delete from template_instance");
    jdbc.update("delete from milestone");
    jdbc.update("delete from project_risk");
    jdbc.update("delete from stage_instance");
    jdbc.update("delete from project_member");
    jdbc.update("delete from delivery_project");
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
  }

  @Test
  void projectStartsWithSevenOrderedStages() {
    ProjectView project = projects.create(command("PRJ-600"));

    assertEquals(Arrays.asList("启动", "需求采集", "二开实施", "上线切换", "试运行与移交", "标准化评估", "项目收尾"),
        project.getStages().stream().map(StageView::getName).collect(Collectors.toList()));
    assertEquals("START", project.getCurrentStage());
    assertEquals("ACTIVE", project.getStatus());
  }

  @Test
  void persistedGateModeControlsWhetherBlockingGateStopsAdvance() {
    ProjectView project = projects.create(command("PRJ-601"));
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
        IllegalArgumentException.class, () -> projects.create(command("PRJ-602", 600, 601)));
    assertEquals("产品或版本不可用于新项目", planningVersion.getMessage());

    jdbc.update("insert into product(id,organization_id,code,name,status) "
        + "values (601,600,'CRM','CRM','PLANNING')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (602,601,'V1','RELEASED')");
    assertThrows(IllegalArgumentException.class,
        () -> projects.create(command("PRJ-603", 601, 602)));

    assertThrows(IllegalArgumentException.class,
        () -> projects.create(new CreateProjectCommand(601, "PRJ-604", "跨组织项目", "客户",
            600, 600, 600, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), "BLOCK")));
  }

  @Test
  void closedProjectCannotBeReopenedAndUnknownStatusIsRejected() {
    ProjectView project = projects.create(command("PRJ-602"));

    ProjectView closing = projects.updateSettings(project.getId(), project.getName(), "CLOSING",
        project.getRiskLevel(), project.getGateMode(), project.getPlannedEndDate(),
        project.getVersion(), manager());
    ProjectView closed = projects.updateSettings(project.getId(), closing.getName(), "CLOSED",
        closing.getRiskLevel(), closing.getGateMode(), closing.getPlannedEndDate(),
        closing.getVersion(), manager());

    assertThrows(ConflictException.class, () -> projects.updateSettings(closed.getId(),
        closed.getName(), "ACTIVE", closed.getRiskLevel(), closed.getGateMode(),
        closed.getPlannedEndDate(), closed.getVersion(), manager()));
    assertThrows(IllegalArgumentException.class, () -> projects.updateSettings(closed.getId(),
        closed.getName(), "HACKED", closed.getRiskLevel(), closed.getGateMode(),
        closed.getPlannedEndDate(), closed.getVersion(), manager()));
  }

  private CreateProjectCommand command(String code) {
    return command(code, 600, 600);
  }

  private CreateProjectCommand command(String code, long productId, long versionId) {
    return new CreateProjectCommand(600, code, "华东银行核心系统交付", "华东银行",
        productId, versionId, 600, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), "BLOCK");
  }

  private CurrentUser manager() {
    return new CurrentUser(600L, 600L, "manager", "交付负责人",
        Collections.singletonList("DELIVERY_MANAGER"),
        Collections.singletonList("project:write"));
  }
}
