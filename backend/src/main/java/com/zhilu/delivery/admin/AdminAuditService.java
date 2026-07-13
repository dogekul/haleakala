package com.zhilu.delivery.admin;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditService {
  private final JdbcTemplate jdbc;

  public AdminAuditService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Map<String, Object> search(long organizationId, String keyword, String action,
      String resourceType, LocalDateTime from, LocalDateTime to, int page, int pageSize) {
    int safePage = Math.max(1, page);
    int safePageSize = Math.max(1, Math.min(100, pageSize));
    StringBuilder where = new StringBuilder(" where a.organization_id=?");
    List<Object> arguments = new ArrayList<Object>();
    arguments.add(organizationId);
    if (!blank(keyword)) {
      where.append(" and (lower(coalesce(u.display_name,'')) like ?"
          + " or lower(a.action) like ? or lower(a.resource_type) like ?"
          + " or lower(coalesce(a.resource_id,'')) like ?"
          + " or lower(a.trace_id) like ? or lower(coalesce(a.details_text,'')) like ?)");
      String like = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
      for (int index = 0; index < 6; index++) arguments.add(like);
    }
    if (!blank(action)) {
      where.append(" and a.action=?");
      arguments.add(action.trim());
    }
    if (!blank(resourceType)) {
      where.append(" and a.resource_type=?");
      arguments.add(resourceType.trim());
    }
    if (from != null) {
      where.append(" and a.created_at>=?");
      arguments.add(Timestamp.valueOf(from));
    }
    if (to != null) {
      where.append(" and a.created_at<=?");
      arguments.add(Timestamp.valueOf(to));
    }
    String join = " from audit_log a left join app_user u on u.id=a.actor_user_id";
    Long total = jdbc.queryForObject("select count(*)" + join + where, Long.class,
        arguments.toArray());
    List<Object> pageArguments = new ArrayList<Object>(arguments);
    pageArguments.add(safePageSize);
    pageArguments.add((safePage - 1) * safePageSize);
    List<Map<String, Object>> items = jdbc.query(
        "select a.id,a.actor_user_id,u.display_name actor_name,a.action,a.resource_type,"
            + "a.resource_id,a.trace_id,a.details_text,a.created_at" + join + where
            + " order by a.created_at desc,a.id desc limit ? offset ?",
        (row, index) -> {
          Map<String, Object> item = new LinkedHashMap<String, Object>();
          item.put("id", row.getLong("id"));
          item.put("actorUserId", row.getObject("actor_user_id"));
          item.put("actorName", row.getString("actor_name"));
          item.put("action", row.getString("action"));
          item.put("resourceType", row.getString("resource_type"));
          item.put("resourceId", row.getString("resource_id"));
          item.put("traceId", row.getString("trace_id"));
          item.put("details", row.getString("details_text"));
          Timestamp created = row.getTimestamp("created_at");
          item.put("createdAt", created == null ? null : created.toLocalDateTime());
          return item;
        }, pageArguments.toArray());
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("items", items);
    result.put("page", safePage);
    result.put("pageSize", safePageSize);
    result.put("total", total == null ? 0 : total);
    return result;
  }

  private boolean blank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
