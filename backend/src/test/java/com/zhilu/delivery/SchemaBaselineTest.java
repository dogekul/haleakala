package com.zhilu.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:delivery;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
class SchemaBaselineTest {

  @Autowired
  private JdbcTemplate jdbc;

  @Test
  void flywayCreatesPlatformFoundationTables() {
    Integer count = jdbc.queryForObject(
        "select count(*) from information_schema.tables "
            + "where table_schema = 'public' and table_name in "
            + "('organization','team','app_user','role','permission','user_role',"
            + "'role_permission','product','product_version','file_object','audit_log','system_setting')",
        Integer.class);

    assertEquals(12, count);
  }

  @Test
  void flywayCreatesProductCenterTablesAndPermissions() {
    Integer tables = jdbc.queryForObject(
        "select count(*) from information_schema.tables where table_schema='public' "
            + "and table_name in ('product_module','product_feature','product_version_feature',"
            + "'requirement_product_feature','standardization_debt_requirement')",
        Integer.class);
    Integer permissions = jdbc.queryForObject(
        "select count(*) from permission where code in ('product:read','product:write')",
        Integer.class);
    assertEquals(5, tables);
    assertEquals(2, permissions);
  }

  @Test
  void upgradesLegacyMaintenanceProductToSunset() {
    String url = "jdbc:h2:mem:legacy-maintenance;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
        + "DB_CLOSE_DELAY=-1";
    DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
    Flyway.configure().dataSource(dataSource).target(MigrationVersion.fromVersion("9"))
        .load().migrate();
    JdbcTemplate legacy = new JdbcTemplate(dataSource);
    legacy.update("insert into organization(id,name,code) values (991,'旧组织','LEGACY-ORG')");
    legacy.update("insert into product(id,owner_user_id,code,name,status) "
        + "values (991,null,'LEGACY-PRODUCT','旧产品','MAINTENANCE')");

    Flyway.configure().dataSource(dataSource).load().migrate();

    assertEquals("SUNSET", legacy.queryForObject(
        "select status from product where id=991", String.class));
  }
}
