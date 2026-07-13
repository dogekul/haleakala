package com.zhilu.delivery.admin;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemSettingService {
  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private final JdbcTemplate jdbc;
  private final long defaultAgentTimeoutMinutes;

  public SystemSettingService(JdbcTemplate jdbc,
      @Value("${delivery.agent.timeout-minutes:30}") long defaultAgentTimeoutMinutes) {
    this.jdbc = jdbc;
    this.defaultAgentTimeoutMinutes = defaultAgentTimeoutMinutes;
  }

  public Map<String, Object> get(long organizationId) {
    Map<String, String> stored = new LinkedHashMap<String, String>();
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select setting_key,setting_value from system_setting where organization_id=?",
        organizationId);
    for (Map<String, Object> row : rows) {
      stored.put(String.valueOf(row.get("setting_key")),
          row.get("setting_value") == null ? null : String.valueOf(row.get("setting_value")));
    }
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("platformName", value(stored, "platform.name", "智鹿交付"));
    values.put("environmentLabel",
        value(stored, "platform.environmentLabel", "内部生产环境"));
    values.put("timezone", value(stored, "platform.timezone", "Asia/Shanghai"));
    values.put("supportEmail", value(stored, "platform.supportEmail", ""));
    values.put("agentTimeoutMinutes", parseLong(
        stored.get("agent.timeoutMinutes"), defaultAgentTimeoutMinutes));
    return values;
  }

  @Transactional
  public Map<String, Object> save(long organizationId, String platformName,
      String environmentLabel, String timezone, String supportEmail,
      long agentTimeoutMinutes) {
    String name = required(platformName, 40, "平台名称");
    String environment = required(environmentLabel, 20, "环境标识");
    String zone = required(timezone, 80, "默认时区");
    try {
      ZoneId.of(zone);
    } catch (DateTimeException invalid) {
      throw new IllegalArgumentException("默认时区无效");
    }
    String email = supportEmail == null ? "" : supportEmail.trim();
    if (!email.isEmpty() && (!EMAIL.matcher(email).matches() || email.length() > 160)) {
      throw new IllegalArgumentException("支持邮箱无效");
    }
    if (agentTimeoutMinutes < 1 || agentTimeoutMinutes > 240) {
      throw new IllegalArgumentException("Agent 超时时间必须在 1 到 240 分钟之间");
    }
    upsert(organizationId, "platform.name", name);
    upsert(organizationId, "platform.environmentLabel", environment);
    upsert(organizationId, "platform.timezone", zone);
    upsert(organizationId, "platform.supportEmail", email);
    upsert(organizationId, "agent.timeoutMinutes", Long.toString(agentTimeoutMinutes));
    return get(organizationId);
  }

  public long agentTimeoutMinutes(long organizationId) {
    List<String> values = jdbc.queryForList(
        "select setting_value from system_setting where organization_id=? and setting_key=?",
        String.class, organizationId, "agent.timeoutMinutes");
    return values.isEmpty() ? defaultAgentTimeoutMinutes
        : parseLong(values.get(0), defaultAgentTimeoutMinutes);
  }

  private void upsert(long organizationId, String key, String value) {
    int changed = jdbc.update("update system_setting set setting_value=?,encrypted=false,"
            + "updated_at=current_timestamp,version=version+1 "
            + "where organization_id=? and setting_key=?",
        value, organizationId, key);
    if (changed == 0) {
      jdbc.update("insert into system_setting(organization_id,setting_key,setting_value,encrypted) "
          + "values (?,?,?,false)", organizationId, key, value);
    }
  }

  private String required(String value, int max, String label) {
    if (value == null || value.trim().isEmpty() || value.trim().length() > max) {
      throw new IllegalArgumentException(label + "不能为空且最多 " + max + " 个字符");
    }
    return value.trim();
  }

  private String value(Map<String, String> values, String key, String fallback) {
    String value = values.get(key);
    return value == null ? fallback : value;
  }

  private long parseLong(String value, long fallback) {
    if (value == null) return fallback;
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException invalid) {
      return fallback;
    }
  }
}
