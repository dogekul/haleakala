package com.zhilu.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

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
}
