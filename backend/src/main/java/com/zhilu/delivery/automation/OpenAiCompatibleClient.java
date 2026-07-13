package com.zhilu.delivery.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class OpenAiCompatibleClient implements AiClient {
  private final ObjectMapper json;
  private final RestTemplate http;
  private final String baseUrl;
  private final String model;
  private final String apiKey;

  public OpenAiCompatibleClient(ObjectMapper json,
      @Value("${delivery.ai.base-url:}") String baseUrl,
      @Value("${delivery.ai.model:}") String model,
      @Value("${delivery.ai.api-key:}") String apiKey,
      @Value("${delivery.ai.connect-timeout-ms:3000}") int connectTimeout,
      @Value("${delivery.ai.read-timeout-ms:30000}") int readTimeout) {
    this.json = json; this.baseUrl = baseUrl; this.model = model; this.apiKey = apiKey;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout); factory.setReadTimeout(readTimeout);
    this.http = new RestTemplate(factory);
  }

  @Override
  public JsonNode completeJson(String systemPrompt, String userPrompt, JsonNode schema) {
    if (blank(baseUrl) || blank(model) || blank(apiKey)) throw new AiNotConfiguredException();
    ObjectNode request = json.createObjectNode();
    request.put("model", model); request.put("temperature", 0.1);
    ArrayNode messages = request.putArray("messages");
    messages.add(message("system", systemPrompt));
    messages.add(message("user", userPrompt));
    ObjectNode format = request.putObject("response_format");
    format.put("type", "json_schema");
    ObjectNode definition = format.putObject("json_schema");
    definition.put("name", "delivery_response"); definition.put("strict", true);
    definition.set("schema", schema);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(apiKey);
    JsonNode response = http.postForObject(endpoint(), new HttpEntity<JsonNode>(request, headers), JsonNode.class);
    JsonNode content = response == null ? null : response.path("choices").path(0).path("message").path("content");
    if (content == null || !content.isTextual()) throw new IllegalStateException("AI 返回格式无效");
    try { return json.readTree(content.asText()); }
    catch (java.io.IOException invalid) { throw new IllegalStateException("AI 未返回合法 JSON", invalid); }
  }

  private ObjectNode message(String role, String content) {
    ObjectNode value = json.createObjectNode(); value.put("role", role); value.put("content", content); return value;
  }
  private String endpoint() {
    String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    return root.endsWith("/v1") ? root + "/chat/completions" : root + "/v1/chat/completions";
  }
  private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
