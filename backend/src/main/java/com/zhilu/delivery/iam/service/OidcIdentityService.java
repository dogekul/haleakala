package com.zhilu.delivery.iam.service;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OidcIdentityService {
  private final JdbcTemplate jdbc;
  private final IamService iam;

  public OidcIdentityService(JdbcTemplate jdbc, IamService iam) {
    this.jdbc = jdbc;
    this.iam = iam;
  }

  @Transactional
  public CurrentUser authenticate(
      String provider, String subject, String email, boolean emailVerified) {
    List<Long> linkedUsers = jdbc.queryForList(
        "select user_id from sso_identity where provider=? and subject=?",
        Long.class,
        provider,
        subject);
    if (!linkedUsers.isEmpty()) {
      return iam.currentUser(linkedUsers.get(0));
    }
    if (!emailVerified || email == null || email.trim().isEmpty()) {
      throw new BadCredentialsException("SSO 账号尚未绑定");
    }
    List<Long> emailUsers = jdbc.queryForList(
        "select id from app_user where lower(email)=lower(?) and status='ACTIVE'",
        Long.class,
        email.trim());
    if (emailUsers.size() != 1) {
      throw new BadCredentialsException("SSO 邮箱未匹配到唯一的启用用户");
    }
    long userId = emailUsers.get(0);
    jdbc.update(
        "insert into sso_identity(user_id,provider,subject,email) values (?,?,?,?)",
        userId,
        provider,
        subject,
        email.trim());
    return iam.currentUser(userId);
  }
}
