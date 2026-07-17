package com.zhilu.delivery.document;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.security.SettingSecretCipher;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutlineConfigurationService {
  private static final String BASE_URL = "outline.baseUrl";
  private static final String PUBLIC_BASE_URL = "outline.publicBaseUrl";
  private static final String API_TOKEN = "outline.apiToken";
  private static final String COLLECTION_ID = "outline.collectionId";
  private static final String COLLECTION_NAME = "outline.collectionName";

  private final JdbcTemplate jdbc;
  private final OutlineProperties properties;
  private final SettingSecretCipher cipher;

  public OutlineConfigurationService(
      JdbcTemplate jdbc, OutlineProperties properties, SettingSecretCipher cipher) {
    this.jdbc = jdbc;
    this.properties = properties;
    this.cipher = cipher;
  }

  public OutlineConnection resolve(long organizationId) {
    Map<String, String> values = new LinkedHashMap<String, String>();
    Map<String, Boolean> encrypted = new LinkedHashMap<String, Boolean>();
    List<Map<String, Object>> rows = jdbc.queryForList(
        "select setting_key,setting_value,encrypted from system_setting "
            + "where organization_id=? and setting_key like 'outline.%'",
        organizationId);
    for (Map<String, Object> row : rows) {
      String key = String.valueOf(row.get("setting_key"));
      Object value = row.get("setting_value");
      values.put(key, value == null ? null : String.valueOf(value));
      encrypted.put(key, Boolean.TRUE.equals(row.get("encrypted")));
    }

    String token;
    if (values.containsKey(API_TOKEN)) {
      if (!Boolean.TRUE.equals(encrypted.get(API_TOKEN))) {
        throw new IllegalStateException("Outline API Token 必须以密文存储");
      }
      token = cipher.decrypt(values.get(API_TOKEN));
    } else {
      token = trim(properties.getApiToken());
    }

    String baseUrl = normalizeBaseUrl(value(values, BASE_URL, properties.getBaseUrl()));
    String publicBaseUrl = value(values, PUBLIC_BASE_URL, properties.getPublicBaseUrl());
    publicBaseUrl = blank(publicBaseUrl) ? baseUrl : normalizeBaseUrl(publicBaseUrl);
    String collectionId = trim(value(values, COLLECTION_ID, properties.getCollectionId()));
    String collectionName = trim(value(values, COLLECTION_NAME, ""));

    boolean anyOrganizationValue = values.containsKey(BASE_URL)
        || values.containsKey(PUBLIC_BASE_URL)
        || values.containsKey(API_TOKEN)
        || values.containsKey(COLLECTION_ID);
    boolean allOrganizationValues = values.containsKey(BASE_URL)
        && values.containsKey(PUBLIC_BASE_URL)
        && values.containsKey(API_TOKEN)
        && values.containsKey(COLLECTION_ID);
    String source = allOrganizationValues ? "ORGANIZATION"
        : anyOrganizationValue ? "MIXED" : "ENVIRONMENT";
    return new OutlineConnection(
        organizationId, baseUrl, publicBaseUrl, token,
        collectionId, collectionName, source);
  }

  public Map<String, Object> view(long organizationId) {
    OutlineConnection connection = resolve(organizationId);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("baseUrl", connection.getBaseUrl());
    result.put("publicBaseUrl", connection.getPublicBaseUrl());
    result.put("collectionId", connection.getCollectionId());
    result.put("collectionName", connection.getCollectionName());
    result.put("apiTokenConfigured", !blank(connection.getApiToken()));
    result.put("source", connection.getSource());
    return result;
  }

  public OutlineConfigurationDraft draft(
      long organizationId, String baseUrl, String publicBaseUrl,
      String apiToken, String collectionReference) {
    OutlineConnection current = resolve(organizationId);
    String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
    String normalizedPublicBaseUrl = blank(publicBaseUrl)
        ? normalizedBaseUrl : normalizeBaseUrl(publicBaseUrl);
    boolean tokenChanged = !blank(apiToken);
    String effectiveToken = tokenChanged ? apiToken.trim() : current.getApiToken();
    if (blank(effectiveToken)) {
      throw new IllegalArgumentException("Outline API Token 不能为空");
    }
    String normalizedCollectionReference = normalizeCollectionReference(collectionReference);
    OutlineConnection connection = new OutlineConnection(
        organizationId, normalizedBaseUrl, normalizedPublicBaseUrl, effectiveToken,
        normalizedCollectionReference, current.getCollectionName(), current.getSource());
    return new OutlineConfigurationDraft(
        connection, normalizedCollectionReference, tokenChanged);
  }

  @Transactional
  public Map<String, Object> saveValidated(
      long organizationId, OutlineConfigurationDraft draft,
      OutlineCollection verifiedCollection) {
    String collectionId = trim(verifiedCollection == null ? null : verifiedCollection.getId());
    if (blank(collectionId)) {
      throw new IllegalArgumentException("Outline Collection 无效");
    }
    List<String> existingCollections = jdbc.queryForList(
        "select distinct outline_collection_id from outline_document_link "
            + "where organization_id=? and outline_document_id is not null",
        String.class, organizationId);
    for (String existingCollection : existingCollections) {
      if (!blank(existingCollection) && !collectionId.equals(existingCollection)) {
        throw new ConflictException(
            "当前组织已有文档，不能直接更换 Collection；请先完成迁移方案");
      }
    }

    OutlineConnection connection = draft.getConnection();
    upsert(organizationId, BASE_URL, connection.getBaseUrl(), false);
    upsert(organizationId, PUBLIC_BASE_URL, connection.getPublicBaseUrl(), false);
    upsert(organizationId, COLLECTION_ID, collectionId, false);
    upsert(organizationId, COLLECTION_NAME, trim(verifiedCollection.getName()), false);
    if (draft.isTokenChanged()) {
      upsert(organizationId, API_TOKEN, cipher.encrypt(connection.getApiToken()), true);
    }
    return view(organizationId);
  }

  public String documentUrl(OutlineConnection connection, String urlId) {
    if (blank(urlId)) return null;
    return connection.getPublicBaseUrl() + "/doc/" + urlId;
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
              + "values (?,?,?,?)",
          organizationId, key, value, encrypted);
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
          || !(blank(path) || "/".equals(path))) {
        throw new IllegalArgumentException("Outline Base URL 无效");
      }
      return candidate.endsWith("/")
          ? candidate.substring(0, candidate.length() - 1) : candidate;
    } catch (URISyntaxException invalid) {
      throw new IllegalArgumentException("Outline Base URL 无效", invalid);
    }
  }

  private String normalizeCollectionReference(String value) {
    String candidate = trim(value);
    if (blank(candidate)) {
      throw new IllegalArgumentException("Outline Collection 不能为空");
    }
    try {
      URI uri = new URI(candidate);
      if (!uri.isAbsolute()) {
        if (candidate.contains("/") || uri.getQuery() != null || uri.getFragment() != null) {
          throw new IllegalArgumentException("Outline Collection 链接无效");
        }
        return candidate;
      }
      String scheme = uri.getScheme();
      if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getQuery() != null
          || uri.getFragment() != null) {
        throw new IllegalArgumentException("Outline Collection 链接无效");
      }
      String path = uri.getPath();
      if (path != null && path.endsWith("/")) {
        path = path.substring(0, path.length() - 1);
      }
      String prefix = "/collection/";
      if (path == null || !path.startsWith(prefix)
          || path.length() == prefix.length()
          || path.substring(prefix.length()).contains("/")) {
        throw new IllegalArgumentException("Outline Collection 链接无效");
      }
      return path.substring(prefix.length());
    } catch (URISyntaxException invalid) {
      throw new IllegalArgumentException("Outline Collection 链接无效", invalid);
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
