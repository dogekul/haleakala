package com.zhilu.delivery.iam.service;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IamAdminService {
  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwordEncoder;

  public IamAdminService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
    this.jdbc = jdbc;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public Map<String, Object> createTeam(
      long organizationId, Long parentId, String name, String code) {
    validateTeam(organizationId, parentId, null);
    try {
      KeyHolder keys = new GeneratedKeyHolder();
      jdbc.update(connection -> {
        PreparedStatement statement = connection.prepareStatement(
            "insert into team(organization_id,parent_id,name,code) values (?,?,?,?)",
            new String[] {"id"});
        statement.setLong(1, organizationId);
        setNullableLong(statement, 2, parentId);
        statement.setString(3, name.trim());
        statement.setString(4, code.trim());
        return statement;
      }, keys);
      return team(organizationId, generatedId(keys));
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("团队编码已存在");
    }
  }

  public List<Map<String, Object>> teams(long organizationId) {
    return jdbc.query(
        "select id,organization_id,parent_id,name,code,enabled from team "
            + "where organization_id=? order by name,id",
        (row, index) -> teamRow(row), organizationId);
  }

  public List<Map<String, Object>> users(long organizationId) {
    List<Map<String, Object>> values = jdbc.query(
        "select u.id,u.organization_id,u.primary_team_id,t.name primary_team_name,"
            + "u.username,u.display_name,u.email,u.status from app_user u "
            + "left join team t on t.id=u.primary_team_id "
            + "where u.organization_id=? order by u.id",
        (row, index) -> userRow(row), organizationId);
    for (Map<String, Object> value : values) {
      value.put("roles", roleCodes(((Number) value.get("id")).longValue()));
    }
    return values;
  }

  @Transactional
  public Map<String, Object> createLocalUser(
      long organizationId,
      Long primaryTeamId,
      String username,
      String password,
      String displayName,
      String email,
      List<String> roleCodes) {
    validateTeam(organizationId, primaryTeamId, null);
    try {
      KeyHolder keys = new GeneratedKeyHolder();
      jdbc.update(connection -> {
        PreparedStatement statement = connection.prepareStatement(
            "insert into app_user(organization_id,primary_team_id,username,password_hash,"
                + "display_name,email,status) values (?,?,?,?,?,?,'ACTIVE')",
            new String[] {"id"});
        statement.setLong(1, organizationId);
        setNullableLong(statement, 2, primaryTeamId);
        statement.setString(3, username.trim());
        statement.setString(4, passwordEncoder.encode(password));
        statement.setString(5, displayName.trim());
        statement.setString(6, normalize(email));
        return statement;
      }, keys);
      long userId = generatedId(keys);
      replaceUserRoles(userId, roleCodes);
      return user(organizationId, userId);
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("用户名或邮箱已存在");
    }
  }

  @Transactional
  public Map<String, Object> updateUser(CurrentUser actor, long userId, String displayName,
      String email, Long primaryTeamId, List<String> roleCodes, String status) {
    validateStatus(status);
    validateTeam(actor.getOrganizationId(), primaryTeamId, null);
    if (actor.getId() == userId && "DISABLED".equals(status)) {
      throw new ConflictException("不能停用当前登录用户");
    }
    try {
      int changed = jdbc.update("update app_user set primary_team_id=?,display_name=?,email=?,"
              + "status=?,updated_at=current_timestamp,version=version+1 "
              + "where id=? and organization_id=?",
          primaryTeamId, displayName.trim(), normalize(email), status,
          userId, actor.getOrganizationId());
      if (changed == 0) throw new NotFoundException("用户不存在");
      replaceUserRoles(userId, roleCodes);
      return user(actor.getOrganizationId(), userId);
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("邮箱已存在");
    }
  }

  @Transactional
  public Map<String, Object> updateTeam(long organizationId, long teamId, Long parentId,
      String name, String code, boolean enabled) {
    validateTeam(organizationId, parentId, teamId);
    try {
      int changed = jdbc.update("update team set parent_id=?,name=?,code=?,enabled=?,"
              + "updated_at=current_timestamp,version=version+1 "
              + "where id=? and organization_id=?",
          parentId, name.trim(), code.trim(), enabled, teamId, organizationId);
      if (changed == 0) throw new NotFoundException("团队不存在");
      return team(organizationId, teamId);
    } catch (DuplicateKeyException duplicate) {
      throw new ConflictException("团队编码已存在");
    }
  }

  public List<Map<String, Object>> roles() {
    List<Long> ids = jdbc.queryForList("select id from role order by id", Long.class);
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Long id : ids) {
      result.add(role(id));
    }
    return result;
  }

  public List<Map<String, Object>> permissions() {
    return jdbc.query("select code,name,module from permission order by module,code",
        (row, index) -> {
          Map<String, Object> item = new LinkedHashMap<String, Object>();
          item.put("code", row.getString("code"));
          item.put("name", row.getString("name"));
          item.put("module", row.getString("module"));
          return item;
        });
  }

  @Transactional
  public Map<String, Object> replacePermissions(long roleId, List<String> permissionCodes) {
    List<String> roleCodes = jdbc.queryForList("select code from role where id=?",
        String.class, roleId);
    if (roleCodes.isEmpty()) throw new NotFoundException("角色不存在");
    LinkedHashSet<String> uniqueCodes = new LinkedHashSet<String>(permissionCodes);
    if ("ADMIN".equals(roleCodes.get(0)) && !uniqueCodes.contains("system:manage")) {
      throw new ConflictException("系统管理员必须保留系统管理权限");
    }
    for (String code : uniqueCodes) {
      Integer count = jdbc.queryForObject("select count(*) from permission where code=?",
          Integer.class, code);
      if (count == null || count == 0) throw new IllegalArgumentException("权限不存在: " + code);
    }
    jdbc.update("delete from role_permission where role_id=?", roleId);
    for (String code : uniqueCodes) {
      Long permissionId = jdbc.queryForObject(
          "select id from permission where code=?", Long.class, code);
      jdbc.update(
          "insert into role_permission(role_id,permission_id) values (?,?)",
          roleId,
          permissionId);
    }
    return role(roleId);
  }

  public void updateUserStatus(CurrentUser actor, long userId, String status) {
    validateStatus(status);
    if (actor.getId() == userId && "DISABLED".equals(status)) {
      throw new ConflictException("不能停用当前登录用户");
    }
    int changed = jdbc.update("update app_user set status=?,version=version+1 "
            + "where id=? and organization_id=?", status, userId, actor.getOrganizationId());
    if (changed == 0) throw new NotFoundException("用户不存在");
  }

  private Map<String, Object> team(long organizationId, long id) {
    List<Map<String, Object>> values = jdbc.query(
        "select id,organization_id,parent_id,name,code,enabled from team "
            + "where id=? and organization_id=?",
        (row, index) -> teamRow(row), id, organizationId);
    if (values.isEmpty()) throw new NotFoundException("团队不存在");
    return values.get(0);
  }

  private long generatedId(KeyHolder keys) {
    Map<String, Object> values = keys.getKeys();
    Object value = values.get("id");
    if (value == null && values.size() == 1) {
      value = values.values().iterator().next();
    }
    if (!(value instanceof Number)) {
      throw new IllegalStateException("Database did not return a generated id");
    }
    return ((Number) value).longValue();
  }

  private Map<String, Object> user(long organizationId, long id) {
    List<Map<String, Object>> values = jdbc.query(
        "select u.id,u.organization_id,u.primary_team_id,t.name primary_team_name,"
            + "u.username,u.display_name,u.email,u.status from app_user u "
            + "left join team t on t.id=u.primary_team_id "
            + "where u.id=? and u.organization_id=?",
        (row, index) -> userRow(row), id, organizationId);
    if (values.isEmpty()) throw new NotFoundException("用户不存在");
    Map<String, Object> value = values.get(0);
    value.put("roles", roleCodes(id));
    return value;
  }

  private Map<String, Object> teamRow(java.sql.ResultSet row) throws java.sql.SQLException {
    Map<String, Object> item = new LinkedHashMap<String, Object>();
    item.put("id", row.getLong("id"));
    item.put("organizationId", row.getLong("organization_id"));
    item.put("parentId", row.getObject("parent_id"));
    item.put("name", row.getString("name"));
    item.put("code", row.getString("code"));
    item.put("enabled", row.getBoolean("enabled"));
    return item;
  }

  private Map<String, Object> userRow(java.sql.ResultSet row) throws java.sql.SQLException {
    Map<String, Object> item = new LinkedHashMap<String, Object>();
    item.put("id", row.getLong("id"));
    item.put("organizationId", row.getLong("organization_id"));
    item.put("primaryTeamId", row.getObject("primary_team_id"));
    item.put("primaryTeamName", row.getString("primary_team_name"));
    item.put("username", row.getString("username"));
    item.put("displayName", row.getString("display_name"));
    item.put("email", row.getString("email"));
    item.put("status", row.getString("status"));
    return item;
  }

  private List<String> roleCodes(long userId) {
    return jdbc.queryForList("select r.code from role r join user_role ur on ur.role_id=r.id "
        + "where ur.user_id=? order by r.code", String.class, userId);
  }

  private void replaceUserRoles(long userId, List<String> roleCodes) {
    jdbc.update("delete from user_role where user_id=?", userId);
    for (String roleCode : roleCodes) {
      List<Long> roleIds = jdbc.queryForList("select id from role where code=?", Long.class, roleCode);
      if (roleIds.isEmpty()) throw new IllegalArgumentException("角色不存在: " + roleCode);
      jdbc.update("insert into user_role(user_id,role_id) values (?,?)", userId, roleIds.get(0));
    }
  }

  private void validateTeam(long organizationId, Long teamId, Long currentTeamId) {
    if (teamId == null) return;
    if (teamId.equals(currentTeamId)) throw new IllegalArgumentException("上级团队不能是自身");
    Integer count = jdbc.queryForObject(
        "select count(*) from team where id=? and organization_id=?", Integer.class,
        teamId, organizationId);
    if (count == null || count == 0) throw new IllegalArgumentException("团队不存在或不属于当前组织");
  }

  private void validateStatus(String status) {
    if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
      throw new IllegalArgumentException("用户状态不受支持");
    }
  }

  private void setNullableLong(PreparedStatement statement, int index, Long value)
      throws java.sql.SQLException {
    if (value == null) statement.setNull(index, Types.BIGINT);
    else statement.setLong(index, value);
  }

  private String normalize(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }

  private Map<String, Object> role(long id) {
    Map<String, Object> item = jdbc.queryForObject(
        "select id,code,name,description,built_in from role where id=?",
        (row, index) -> {
          Map<String, Object> role = new LinkedHashMap<String, Object>();
          role.put("id", row.getLong("id"));
          role.put("code", row.getString("code"));
          role.put("name", row.getString("name"));
          role.put("description", row.getString("description"));
          role.put("builtIn", row.getBoolean("built_in"));
          return role;
        },
        id);
    item.put("permissions", jdbc.queryForList(
        "select p.code from permission p join role_permission rp on rp.permission_id=p.id "
            + "where rp.role_id=? order by p.code",
        String.class,
        id));
    return item;
  }
}
