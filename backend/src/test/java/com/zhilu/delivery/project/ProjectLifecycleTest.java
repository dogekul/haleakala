package com.zhilu.delivery.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zhilu.delivery.common.error.ConflictException;
import java.time.LocalDate;
import java.util.Arrays;
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
        + "values (600,600,'V5.2','ACTIVE')");
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
  void blockingGateStopsAdvanceWhileWarningModeRecordsAndAdvances() {
    ProjectView project = projects.create(command("PRJ-601"));
    projects.setGate(project.getId(), "START", "BLOCKING", "启动检查单未完成", 600);

    assertThrows(ConflictException.class, () -> projects.advanceStage(
        project.getId(), DeliveryStage.REQUIREMENT, GateMode.BLOCK, 600));
    ProjectView advanced = projects.advanceStage(
        project.getId(), DeliveryStage.REQUIREMENT, GateMode.WARNING, 600);

    assertEquals("REQUIREMENT", advanced.getCurrentStage());
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from project_activity where project_id=? and action='STAGE_ADVANCED_WITH_WARNING'",
        Integer.class, project.getId()));
  }

  private CreateProjectCommand command(String code) {
    return new CreateProjectCommand(600, code, "华东银行核心系统交付", "华东银行",
        600, 600, 600, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), "BLOCK");
  }
}
