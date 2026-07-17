package com.zhilu.delivery.automation;

import com.zhilu.delivery.common.security.SettingSecretCipher;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiConfigurationService {
  private static final String BASE_URL = "ai.baseUrl";
  private static final String MODEL = "ai.model";
  private static final String API_KEY = "ai.apiKey";

  private final JdbcTemplate jdbc;
  private final SettingSecretCipher cipher;
  private final String environmentBaseUrl;
  private final String environmentModel;
  private final String environmentApiKey;

  public AiConfigurationService(JdbcTemplate jdbc, SettingSecretCipher cipher,
      @Value("${delivery.ai.base-url:}") String environmentBaseUrl,
      @Value("${delivery.ai.model:}") String environmentModel,
      @Value("${delivery.ai.api-key:}") String environmentApiKey) {
    this.jdbc = jdbc;
    this.cipher = cipher;
    this.environmentBaseUrl = environmentBaseUrl;
    this.environmentModel = environmentModel;
    this.environmentApiKey = environmentApiKey;
  }

  public AiConnection resolve(long organizationId) {
    Map<String, String> values = new LinkedHashMap<String, String>();
    Map<String, Boolean> encrypted = new LinkedHashMap<String, Boolean>();
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select setting_key,setting_value,encrypted from system_setting "
            + "where organization_id=? and setting_key like 'ai.%'", organizationId);
    for (Map<String, Object> row : rows) {
      String key = String.valueOf(row.get("setting_key"));
      Object value = row.get("setting_value");
      values.put(key, value == null ? null : String.valueOf(value));
      encrypted.put(key, Boolean.TRUE.equals(row.get("encrypted")));
    }

    String apiKey;
    if (values.containsKey(API_KEY)) {
      if (!Boolean.TRUE.equals(encrypted.get(API_KEY))) {
        throw new IllegalStateException("AI API Key 必须以密文存储");
      }
      apiKey = cipher.decrypt(values.get(API_KEY));
    } else {
      apiKey = trim(environmentApiKey);
    }
    String rawBaseUrl = value(values, BASE_URL, environmentBaseUrl);
    String baseUrl = blank(rawBaseUrl) ? "" : normalizeBaseUrl(rawBaseUrl);
    String model = trim(value(values, MODEL, environmentModel));
    boolean anyOrganizationValue = values.containsKey(BASE_URL)
        || values.containsKey(MODEL) || values.containsKey(API_KEY);
    boolean allOrganizationValues = values.containsKey(BASE_URL)
        && values.containsKey(MODEL) && values.containsKey(API_KEY);
    String source = allOrganizationValues ? "ORGANIZATION"
        : anyOrganizationValue ? "MIXED" : "ENVIRONMENT";
    return new AiConnection(organizationId, baseUrl, model, apiKey, source);
  }

  public Map<String, Object> view(long organizationId) {
    AiConnection connection = resolve(organizationId);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("baseUrl", connection.getBaseUrl());
    result.put("model", connection.getModel());
    result.put("apiKeyConfigured", !blank(connection.getApiKey()));
    result.put("source", connection.getSource());
    return result;
  }

  public AiConfigurationDraft draft(
      long organizationId, String baseUrl, String model, String apiKey) {
    AiConnection current = resolve(organizationId);
    String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
    String normalizedModel = trim(model);
    if (blank(normalizedModel)) {
      throw new IllegalArgumentException("AI Model 不能为空");
    }
    boolean apiKeyChanged = !blank(apiKey);
    String effectiveApiKey = apiKeyChanged ? apiKey.trim() : current.getApiKey();
    if (blank(effectiveApiKey)) {
      throw new IllegalArgumentException("AI API Key 不能为空");
    }
    return new AiConfigurationDraft(new AiConnection(
        organizationId, normalizedBaseUrl, normalizedModel, effectiveApiKey,
        current.getSource()), apiKeyChanged);
  }

  @Transactional
  public Map<String, Object> saveValidated(long organizationId, AiConfigurationDraft draft) {
    AiConnection connection = draft.getConnection();
    upsert(organizationId, BASE_URL, connection.getBaseUrl(), false);
    upsert(organizationId, MODEL, connection.getModel(), false);
    if (draft.isApiKeyChanged()) {
      upsert(organizationId, API_KEY, cipher.encrypt(connection.getApiKey()), true);
    }
    return view(organizationId);
  }

  private void upsert(long organizationId, String key, String value, boolean encrypted) {
    int changed = jdbc.update(
        "update system_setting set setting_value=?,encrypted=?,"
            + "updated_at=current_timestamp,version=version+1 "
            + "where organization_id=? and setting_key=?",
        value, encrypted, organizationId, key);
    if (changed == 0) {
      jdbc.update(
          "insert into system_setting(organization_id,setting_key,setting_value,encrypted) "
              + "values (?,?,?,?)", organizationId, key, value, encrypted);
    }
  }

  private String normalizeBaseUrl(String value) {
    String candidate = trim(value);
    try {
      URI uri = new URI(candidate);
      String scheme = uri.getScheme();
      String path = uri.getPath();
      if (scheme == null
          || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getQuery() != null
          || uri.getFragment() != null
          || !(blank(path) || "/".equals(path) || "/v1".equals(path)
              || "/v1/".equals(path))) {
        throw new IllegalArgumentException("AI Base URL 无效");
      }
      return candidate.endsWith("/")
          ? candidate.substring(0, candidate.length() - 1) : candidate;
    } catch (URISyntaxException invalid) {
      throw new IllegalArgumentException("AI Base URL 无效", invalid);
    }
  }

  private String value(Map<String, String> values, String key, String fallback) {
    return values.containsKey(key) ? values.get(key) : fallback;
  }

  private String trim(String value) {
    return value == null ? "" : value.trim();
  }

  private boolean blank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
