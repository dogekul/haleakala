package com.zhilu.delivery.iam.service;

import com.zhilu.delivery.iam.domain.AppUser;
import com.zhilu.delivery.iam.repo.UserRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IamService {
  private final UserRepository users;
  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwordEncoder;

  public IamService(UserRepository users, JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.jdbc = jdbc;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional(readOnly = true)
  public CurrentUser authenticate(String username, String password) {
    AppUser user = users.findByUsernameAndStatus(username, "ACTIVE")
        .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));
    if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new BadCredentialsException("用户名或密码错误");
    }
    return currentUser(user);
  }

  @Transactional(readOnly = true)
  public CurrentUser currentUser(long userId) {
    AppUser user = users.findById(userId)
        .orElseThrow(() -> new BadCredentialsException("用户不存在"));
    if (!"ACTIVE".equals(user.getStatus())) {
      throw new BadCredentialsException("用户已停用");
    }
    return currentUser(user);
  }

  @Transactional(readOnly = true)
  public List<UserSummary> listUsers() {
    return users.findAll().stream().map(UserSummary::new).collect(Collectors.toList());
  }

  private CurrentUser currentUser(AppUser user) {
    List<String> roles = jdbc.queryForList(
        "select r.code from role r join user_role ur on ur.role_id=r.id "
            + "where ur.user_id=? order by r.code",
        String.class,
        user.getId());
    List<String> permissions = jdbc.queryForList(
        "select distinct p.code from permission p "
            + "join role_permission rp on rp.permission_id=p.id "
            + "join user_role ur on ur.role_id=rp.role_id "
            + "where ur.user_id=? order by p.code",
        String.class,
        user.getId());
    return new CurrentUser(
        user.getId(),
        user.getOrganizationId(),
        user.getUsername(),
        user.getDisplayName(),
        roles,
        permissions);
  }
}
