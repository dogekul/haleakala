package com.zhilu.delivery.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.iam.service.OidcIdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:oidc;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none"
})
class OidcIdentityServiceTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private OidcIdentityService identities;

  @BeforeEach
  void seedUser() {
    jdbc.update("delete from sso_identity");
    jdbc.update("delete from user_role");
    jdbc.update("delete from app_user");
    jdbc.update("delete from organization");
    jdbc.update("insert into organization(id,name,code) values (400,'智鹿科技','ZHILU-OIDC')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,email,status) "
        + "values (400,400,'sso-user','单点用户','sso@example.com','ACTIVE')");
  }

  @Test
  void verifiedEmailCreatesIdentityAndSubjectCanLoginAgain() {
    CurrentUser first = identities.authenticate(
        "feishu", "open-id-400", "sso@example.com", true);
    CurrentUser second = identities.authenticate(
        "feishu", "open-id-400", null, false);

    assertEquals(Long.valueOf(400), first.getId());
    assertEquals(Long.valueOf(400), second.getId());
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from sso_identity where provider='feishu' and subject='open-id-400'",
        Integer.class));
  }

  @Test
  void unverifiedOrUnknownEmailCannotCreateIdentity() {
    assertThrows(BadCredentialsException.class, () -> identities.authenticate(
        "feishu", "unverified", "sso@example.com", false));
    assertThrows(BadCredentialsException.class, () -> identities.authenticate(
        "feishu", "missing", "missing@example.com", true));
  }
}
