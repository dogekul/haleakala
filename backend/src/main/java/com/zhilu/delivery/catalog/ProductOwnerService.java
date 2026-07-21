package com.zhilu.delivery.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductOwnerService {
  private final JdbcTemplate jdbc;

  public ProductOwnerService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> options(long organizationId) {
    return jdbc.query("select distinct u.id,u.display_name from app_user u "
            + "join user_role ur on ur.user_id=u.id join role r on r.id=ur.role_id "
            + "where u.organization_id=? and u.status='ACTIVE' and r.code='PRODUCT_MANAGER' "
            + "order by u.display_name,u.id",
        (row, index) -> {
          Map<String, Object> value = new LinkedHashMap<String, Object>();
          value.put("id", row.getLong("id"));
          value.put("displayName", row.getString("display_name"));
          return value;
        }, organizationId);
  }

  public void validate(long organizationId, Long ownerUserId) {
    if (ownerUserId == null) return;
    Integer count = jdbc.queryForObject("select count(*) from app_user u "
            + "join user_role ur on ur.user_id=u.id join role r on r.id=ur.role_id "
            + "where u.id=? and u.organization_id=? and u.status='ACTIVE' "
            + "and r.code='PRODUCT_MANAGER'",
        Integer.class, ownerUserId, organizationId);
    if (count == null || count == 0) {
      throw new IllegalArgumentException("请选择当前组织内启用的产品负责人");
    }
  }
}
