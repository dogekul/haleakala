package com.zhilu.delivery.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service
public class HttpOutlineClient implements OutlineClient {
  private final OutlineProperties properties;
  private final ObjectMapper json;
  private final RestTemplate http;

  public HttpOutlineClient(OutlineProperties properties, ObjectMapper json) {
    this.properties = properties;
    this.json = json;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(millis(properties.getConnectTimeout()));
    factory.setReadTimeout(millis(properties.getReadTimeout()));
    this.http = new RestTemplate(factory);
  }

  @Override
  public OutlineDocument create(
      String title, String text, String collectionId, String parentDocumentId, boolean publish) {
    Map<String, Object> body = new LinkedHashMap<String, Object>();
    body.put("title", title);
    body.put("text", text);
    body.put("collectionId", collectionId);
    if (!blank(parentDocumentId)) body.put("parentDocumentId", parentDocumentId);
    body.put("publish", publish);
    return document(post("documents.create", body).path("data"));
  }

  @Override
  public OutlineDocument info(String documentId) {
    Map<String, Object> body = new LinkedHashMap<String, Object>();
    body.put("id", documentId);
    return document(post("documents.info", body).path("data"));
  }

  @Override
  public List<OutlineDocument> children(String parentDocumentId) {
    Map<String, Object> body = new LinkedHashMap<String, Object>();
    body.put("parentDocumentId", parentDocumentId);
    body.put("limit", 100);
    body.put("offset", 0);
    body.put("statusFilter", Arrays.asList("published"));
    JsonNode data = post("documents.list", body).path("data");
    if (!data.isArray()) {
      throw new OutlineException(
          OutlineException.Type.INVALID_RESPONSE, "Outline returned an invalid document list");
    }
    List<OutlineDocument> result = new ArrayList<OutlineDocument>();
    for (JsonNode item : data) result.add(document(item));
    return result;
  }

  @Override
  public OutlineDocument update(String documentId, String title, String text) {
    Map<String, Object> body = new LinkedHashMap<String, Object>();
    body.put("id", documentId);
    body.put("title", title);
    body.put("text", text);
    return document(post("documents.update", body).path("data"));
  }

  @Override
  public String exportMarkdown(String documentId) {
    Map<String, Object> body = new LinkedHashMap<String, Object>();
    body.put("id", documentId);
    JsonNode data = post("documents.export", body).path("data");
    if (!data.isTextual()) {
      throw new OutlineException(
          OutlineException.Type.INVALID_RESPONSE, "Outline returned an invalid export");
    }
    return data.asText();
  }

  @Override
  public boolean isConfigured() {
    return !blank(properties.getBaseUrl())
        && !blank(properties.getApiToken())
        && !blank(properties.getCollectionId());
  }

  private JsonNode post(String method, Map<String, Object> body) {
    if (!isConfigured()) {
      throw new OutlineException(
          OutlineException.Type.NOT_CONFIGURED, "Outline integration is not configured");
    }
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(properties.getApiToken());
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
    try {
      ResponseEntity<String> response = http.postForEntity(
          baseUrl() + "/api/" + method, new HttpEntity<Map<String, Object>>(body, headers),
          String.class);
      return json.readTree(response.getBody());
    } catch (HttpStatusCodeException failure) {
      throw statusFailure(failure);
    } catch (ResourceAccessException failure) {
      if (hasCause(failure, SocketTimeoutException.class)) {
        throw new OutlineException(
            OutlineException.Type.TIMEOUT, "Outline request timed out", failure);
      }
      throw new OutlineException(
          OutlineException.Type.UNAVAILABLE, "Outline is unavailable", failure);
    } catch (JsonProcessingException failure) {
      throw new OutlineException(
          OutlineException.Type.INVALID_RESPONSE, "Outline returned invalid JSON", failure);
    }
  }

  private OutlineDocument document(JsonNode value) {
    if (!value.isObject() || blank(value.path("id").asText(null))) {
      throw new OutlineException(
          OutlineException.Type.INVALID_RESPONSE, "Outline returned an invalid document");
    }
    return new OutlineDocument(
        value.path("id").asText(),
        text(value, "collectionId"),
        text(value, "parentDocumentId"),
        value.path("title").asText(""),
        value.path("text").asText(""),
        value.path("url").asText(""),
        value.path("urlId").asText(""),
        value.path("revision").asLong(),
        instant(value.path("updatedAt").asText(null)));
  }

  private OutlineException statusFailure(HttpStatusCodeException failure) {
    int status = failure.getRawStatusCode();
    OutlineException.Type type;
    if (status == 400) type = OutlineException.Type.VALIDATION;
    else if (status == 401) type = OutlineException.Type.AUTHENTICATION;
    else if (status == 403) type = OutlineException.Type.FORBIDDEN;
    else if (status == 404) type = OutlineException.Type.NOT_FOUND;
    else if (status == 429) type = OutlineException.Type.RATE_LIMIT;
    else type = OutlineException.Type.UNAVAILABLE;
    return new OutlineException(type, "Outline request failed with status " + status, failure);
  }

  private String baseUrl() {
    String value = properties.getBaseUrl().trim();
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private String text(JsonNode value, String field) {
    JsonNode node = value.get(field);
    return node == null || node.isNull() ? null : node.asText();
  }

  private Instant instant(String value) {
    return blank(value) ? null : Instant.parse(value);
  }

  private boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (type.isInstance(current)) return true;
    }
    return false;
  }

  private boolean blank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static int millis(Duration value) {
    long millis = value == null ? 0 : value.toMillis();
    return (int) Math.min(Integer.MAX_VALUE, Math.max(0, millis));
  }
}
