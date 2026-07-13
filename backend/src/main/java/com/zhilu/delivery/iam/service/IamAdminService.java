package com.zhilu.delivery.iam.service;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
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
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      PreparedStatement statement = connection.prepareStatement(
          "insert into team(organization_id,parent_id,name,code) values (?,?,?,?)",
          new String[] {"id"});
      statement.setLong(1, organizationId);
      if (parentId == null) {
        statement.setNull(2, Types.BIGINT);
      } else {
        statement.setLong(2, parentId);
      }
      statement.setString(3, name);
      statement.setString(4, code);
      return statement;
    }, keys);
    return team(generatedId(keys));
  }

  public List<Map<String, Object>> teams() {
    return jdbc.query(
        "select id,organization_id,parent_id,name,code,enabled from team order by name",
        (row, index) -> {
          Map<String, Object> item = new LinkedHashMap<String, Object>();
          item.put("id", row.getLong("id"));
          item.put("organizationId", row.getLong("organization_id"));
          item.put("parentId", row.getObject("parent_id"));
          item.put("name", row.getString("name"));
          item.put("code", row.getString("code"));
          item.put("enabled", row.getBoolean("enabled"));
          return item;
        });
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
    try {
      KeyHolder keys = new GeneratedKeyHolder();
      jdbc.update(connection -> {
        PreparedStatement statement = connection.prepareStatement(
            "insert into app_user(organization_id,primary_team_id,username,password_hash,"
                + "display_name,email,status) values (?,?,?,?,?,?,'ACTIVE')",
            new String[] {"id"});
        statement.setLong(1, organizationId);
        if (primaryTeamId == null) {
          statement.setNull(2, Types.BIGINT);
        } else {
          statement.setLong(2, primaryTeamId);
        }
        statement.setString(3, username);
        statement.setString(4, passwordEncoder.encode(password));
        statement.setString(5, displayName);
        statement.setString(6, email);
        return statement;
      }, keys);
      long userId = generatedId(keys);
      for (String roleCode : roleCodes) {
        Long roleId = jdbc.queryForObject(
            "select id from role where code=?", Long.class, roleCode);
        jdbc.update("insert into user_role(user_id,role_id) values (?,?)", userId, roleId);
      }
      return user(userId);
    } catch (DuplicateKeyException duplicate) {
      throw duplicate;
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

  @Transactional
  public Map<String, Object> replacePermissions(long roleId, List<String> permissionCodes) {
    jdbc.update("delete from role_permission where role_id=?", roleId);
    for (String code : permissionCodes) {
      Long permissionId = jdbc.queryForObject(
          "select id from permission where code=?", Long.class, code);
      jdbc.update(
          "insert into role_permission(role_id,permission_id) values (?,?)",
          roleId,
          permissionId);
    }
    return role(roleId);
  }

  public void updateUserStatus(long userId, String status) {
    jdbc.update("update app_user set status=?,version=version+1 where id=?", status, userId);
  }

  private Map<String, Object> team(long id) {
    return jdbc.queryForObject(
        "select id,organization_id,parent_id,name,code,enabled from team where id=?",
        (row, index) -> {
          Map<String, Object> item = new LinkedHashMap<String, Object>();
          item.put("id", row.getLong("id"));
          item.put("organizationId", row.getLong("organization_id"));
          item.put("parentId", row.getObject("parent_id"));
          item.put("name", row.getString("name"));
          item.put("code", row.getString("code"));
          item.put("enabled", row.getBoolean("enabled"));
          return item;
        },
        id);
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

  private Map<String, Object> user(long id) {
    return jdbc.queryForObject(
        "select id,organization_id,primary_team_id,username,display_name,email,status "
            + "from app_user where id=?",
        (row, index) -> {
          Map<String, Object> item = new LinkedHashMap<String, Object>();
          item.put("id", row.getLong("id"));
          item.put("organizationId", row.getLong("organization_id"));
          item.put("primaryTeamId", row.getObject("primary_team_id"));
          item.put("username", row.getString("username"));
          item.put("displayName", row.getString("display_name"));
          item.put("email", row.getString("email"));
          item.put("status", row.getString("status"));
          return item;
        },
        id);
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
