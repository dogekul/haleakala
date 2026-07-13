package com.zhilu.delivery.audit;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
  private final JdbcTemplate jdbc;

  public AuditService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void record(
      long organizationId,
      Long actorUserId,
      String action,
      String resourceType,
      String resourceId,
      String details) {
    jdbc.update("insert into audit_log(organization_id,actor_user_id,action,resource_type,"
            + "resource_id,trace_id,details_text) values (?,?,?,?,?,?,?)",
        organizationId, actorUserId, action, resourceType, resourceId,
        UUID.randomUUID().toString(), details);
  }
}
