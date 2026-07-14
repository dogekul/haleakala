package com.zhilu.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.FlywayException;
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

  @Test
  void agentDeliveryMigrationRunsAfterAnAlreadyDeployedV11() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:deployed-v11;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa", "");
    Flyway.configure().dataSource(dataSource).target(MigrationVersion.fromVersion("11"))
        .load().migrate();
    JdbcTemplate deployed = new JdbcTemplate(dataSource);
    assertEquals(Integer.valueOf(0), deployed.queryForObject(
        "select count(*) from information_schema.columns where table_schema='public' "
            + "and table_name='agent_job' and column_name='dispatch_status'", Integer.class));

    Flyway.configure().dataSource(dataSource).load().migrate();

    assertEquals(Integer.valueOf(1), deployed.queryForObject(
        "select count(*) from information_schema.columns where table_schema='public' "
            + "and table_name='agent_job' and column_name='dispatch_status'", Integer.class));
  }

  @Test
  void v11AssignsProductsFromProjectReferencesAndValidatesOwners() {
    DriverManagerDataSource dataSource = legacyDataSource("legacy-project-ownership");
    JdbcTemplate legacy = new JdbcTemplate(dataSource);
    organizationAndUser(legacy, 991, "ORG-A");
    organizationAndUser(legacy, 992, "ORG-B");
    productAndVersion(legacy, 991, 992L);
    productAndVersion(legacy, 992, 991L);
    productAndVersion(legacy, 993, 991L);
    productAndVersion(legacy, 994, null);
    project(legacy, 991, 992, 991, 991);
    project(legacy, 992, 992, 992, 992);

    migrateV11(dataSource);

    assertEquals(Long.valueOf(992), legacy.queryForObject(
        "select organization_id from product where id=991", Long.class));
    assertEquals(Long.valueOf(992), legacy.queryForObject(
        "select owner_user_id from product where id=991", Long.class));
    assertEquals(Long.valueOf(992), legacy.queryForObject(
        "select organization_id from product where id=992", Long.class));
    assertNull(legacy.queryForObject(
        "select owner_user_id from product where id=992", Long.class));
    assertEquals(Long.valueOf(991), legacy.queryForObject(
        "select organization_id from product where id=993", Long.class));
    assertEquals(Long.valueOf(991), legacy.queryForObject(
        "select owner_user_id from product where id=993", Long.class));
    assertEquals(Long.valueOf(991), legacy.queryForObject(
        "select organization_id from product where id=994", Long.class));
  }

  @Test
  void v11FailsBeforeSchemaChangesWhenProductIsSharedAcrossOrganizations() {
    DriverManagerDataSource dataSource = legacyDataSource("legacy-shared-product");
    JdbcTemplate legacy = new JdbcTemplate(dataSource);
    organizationAndUser(legacy, 991, "ORG-A");
    organizationAndUser(legacy, 992, "ORG-B");
    productAndVersion(legacy, 991, null);
    project(legacy, 991, 991, 991, 991);
    project(legacy, 992, 992, 991, 991);

    FlywayException failure = assertThrows(FlywayException.class,
        () -> migrateV11(dataSource));

    assertTrue(messages(failure).toLowerCase(java.util.Locale.ROOT)
            .contains("v11_product_single_organization_guard"),
        messages(failure));
    assertEquals(Integer.valueOf(0), legacy.queryForObject(
        "select count(*) from information_schema.columns where table_schema='public' "
            + "and table_name='product' and column_name='organization_id'", Integer.class));
  }

  @Test
  void v11FailsWhenProjectProductAndVersionProductDisagree() {
    DriverManagerDataSource dataSource = legacyDataSource("legacy-broken-project-version");
    JdbcTemplate legacy = new JdbcTemplate(dataSource);
    organizationAndUser(legacy, 991, "ORG-A");
    productAndVersion(legacy, 991, null);
    productAndVersion(legacy, 992, null);
    project(legacy, 991, 991, 991, 992);

    FlywayException failure = assertThrows(FlywayException.class,
        () -> migrateV11(dataSource));

    assertTrue(messages(failure).toLowerCase(java.util.Locale.ROOT)
            .contains("v11_project_product_version_guard"),
        messages(failure));
    assertEquals(Integer.valueOf(0), legacy.queryForObject(
        "select count(*) from information_schema.columns where table_schema='public' "
            + "and table_name='product' and column_name='organization_id'", Integer.class));
  }

  @Test
  void v11UsesDirectAndVersionKnowledgeReferencesForProductOwnership() {
    DriverManagerDataSource dataSource = legacyDataSource("legacy-knowledge-ownership");
    JdbcTemplate legacy = new JdbcTemplate(dataSource);
    organizationAndUser(legacy, 991, "ORG-A");
    organizationAndUser(legacy, 992, "ORG-B");
    productAndVersion(legacy, 991, null);
    productAndVersion(legacy, 992, null);
    legacy.update("insert into knowledge_item(id,organization_id,type,title,summary,content_text,"
        + "product_id,owner_user_id) values (991,992,'CASE','直接产品','摘要','正文',991,992)");
    legacy.update("insert into knowledge_item(id,organization_id,type,title,summary,content_text,"
        + "product_version_id,owner_user_id) "
        + "values (992,992,'CASE','版本产品','摘要','正文',992,992)");

    migrateV11(dataSource);

    assertEquals(Long.valueOf(992), legacy.queryForObject(
        "select organization_id from product where id=991", Long.class));
    assertEquals(Long.valueOf(992), legacy.queryForObject(
        "select organization_id from product where id=992", Long.class));
  }

  @Test
  void v11UsesV7ActorReferencesForProductOwnership() {
    DriverManagerDataSource dataSource = legacyDataSource("legacy-v7-ownership");
    JdbcTemplate legacy = new JdbcTemplate(dataSource);
    organizationAndUser(legacy, 991, "ORG-A");
    organizationAndUser(legacy, 992, "ORG-B");
    organizationAndUser(legacy, 993, "ORG-C");
    organizationAndUser(legacy, 994, "ORG-D");
    productAndVersion(legacy, 991, null);
    productAndVersion(legacy, 992, null);
    productAndVersion(legacy, 993, null);
    legacy.update("insert into product_baseline(id,product_version_id,capability_code,"
        + "capability_name,dimension,scope_description,owner_user_id) "
        + "values (991,991,'BASELINE','基线','FUNCTIONAL','范围',992)");
    legacy.update("insert into maturity_assessment(id,product_version_id,period_key,"
        + "standard_coverage,reuse_rate,documentation_score,extension_readiness,"
        + "delivery_stability,maturity_score,assessed_by) "
        + "values (992,992,'2026-Q1',80,80,80,80,80,80,993)");
    legacy.update("insert into standardization_debt(id,product_version_id,pattern_key,title,"
        + "occurrence_count,distinct_projects,owner_user_id) "
        + "values (993,993,'V7-DEBT','标准化债务',1,1,994)");

    migrateV11(dataSource);

    assertEquals(Long.valueOf(992), legacy.queryForObject(
        "select organization_id from product where id=991", Long.class));
    assertEquals(Long.valueOf(993), legacy.queryForObject(
        "select organization_id from product where id=992", Long.class));
    assertEquals(Long.valueOf(994), legacy.queryForObject(
        "select organization_id from product where id=993", Long.class));
  }

  @Test
  void v11FailsBeforeSchemaChangesWhenV7ActorsSpanOrganizations() {
    DriverManagerDataSource dataSource = legacyDataSource("legacy-v7-shared-product");
    JdbcTemplate legacy = new JdbcTemplate(dataSource);
    organizationAndUser(legacy, 991, "ORG-A");
    organizationAndUser(legacy, 992, "ORG-B");
    productAndVersion(legacy, 991, null);
    legacy.update("insert into product_baseline(id,product_version_id,capability_code,"
        + "capability_name,dimension,scope_description,owner_user_id) "
        + "values (991,991,'BASELINE','基线','FUNCTIONAL','范围',991)");
    legacy.update("insert into maturity_assessment(id,product_version_id,period_key,"
        + "standard_coverage,reuse_rate,documentation_score,extension_readiness,"
        + "delivery_stability,maturity_score,assessed_by) "
        + "values (991,991,'2026-Q1',80,80,80,80,80,80,992)");

    FlywayException failure = assertThrows(FlywayException.class,
        () -> migrateV11(dataSource));

    assertTrue(messages(failure).toLowerCase(java.util.Locale.ROOT)
            .contains("v11_product_single_organization_guard"),
        messages(failure));
    assertEquals(Integer.valueOf(0), legacy.queryForObject(
        "select count(*) from information_schema.columns where table_schema='public' "
            + "and table_name='product' and column_name='organization_id'", Integer.class));
  }

  @Test
  void productPermissionsMatchBuiltInRoleContract() {
    assertEquals(Integer.valueOf(6), jdbc.queryForObject(
        "select count(*) from role r join role_permission rp on rp.role_id=r.id "
            + "join permission p on p.id=rp.permission_id where r.built_in=true "
            + "and p.code='product:read' and r.code in "
            + "('ADMIN','PMO','DELIVERY_MANAGER','DELIVERY_ENGINEER','TECH_MANAGER',"
            + "'PRODUCT_MANAGER')", Integer.class));
    assertEquals(Integer.valueOf(2), jdbc.queryForObject(
        "select count(*) from role r join role_permission rp on rp.role_id=r.id "
            + "join permission p on p.id=rp.permission_id where r.built_in=true "
            + "and p.code='product:write' and r.code in ('ADMIN','PRODUCT_MANAGER')",
        Integer.class));
    assertEquals(Integer.valueOf(0), jdbc.queryForObject(
        "select count(*) from role r join role_permission rp on rp.role_id=r.id "
            + "join permission p on p.id=rp.permission_id where r.built_in=true "
            + "and p.code='product:write' and r.code not in ('ADMIN','PRODUCT_MANAGER')",
        Integer.class));
  }

  private DriverManagerDataSource legacyDataSource(String name) {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        "jdbc:h2:mem:" + name + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa", "");
    Flyway.configure().dataSource(dataSource).target(MigrationVersion.fromVersion("9"))
        .load().migrate();
    return dataSource;
  }

  private void migrateV11(DriverManagerDataSource dataSource) {
    Flyway.configure().dataSource(dataSource).load().migrate();
  }

  private void organizationAndUser(JdbcTemplate legacy, long id, String code) {
    legacy.update("insert into organization(id,name,code) values (?, ?, ?)",
        id, code, code);
    legacy.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (?,?,?,?,'ACTIVE')", id, id, "user-" + id, "用户" + id);
  }

  private void productAndVersion(JdbcTemplate legacy, long id, Long ownerUserId) {
    legacy.update("insert into product(id,owner_user_id,code,name,status) "
        + "values (?,?,?,?, 'ACTIVE')", id, ownerUserId, "PRODUCT-" + id, "产品" + id);
    legacy.update("insert into product_version(id,product_id,version_name,status) "
        + "values (?,?,?, 'ACTIVE')", id, id, "V" + id);
  }

  private void project(JdbcTemplate legacy, long id, long organizationId,
      long productId, long versionId) {
    legacy.update("insert into delivery_project(id,organization_id,code,name,customer_name,"
            + "product_id,product_version_id,manager_user_id,created_by) "
            + "values (?,?,?,?,?,?,?,?,?)",
        id, organizationId, "PROJECT-" + id, "项目" + id, "客户", productId, versionId,
        organizationId, organizationId);
  }

  private String messages(Throwable failure) {
    StringBuilder messages = new StringBuilder();
    for (Throwable current = failure; current != null; current = current.getCause()) {
      messages.append(' ').append(current.getMessage());
    }
    return messages.toString();
  }
}
