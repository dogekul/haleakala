package com.zhilu.delivery.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.SocketTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class OpenAiCompatibleClient implements AiClient {
  private final ObjectMapper json;
  private final RestTemplate http;
  private final AiConfigurationService configurations;

  public OpenAiCompatibleClient(ObjectMapper json,
      AiConfigurationService configurations,
      @Value("${delivery.ai.connect-timeout-ms:3000}") int connectTimeout,
      @Value("${delivery.ai.read-timeout-ms:30000}") int readTimeout) {
    this.json = json;
    this.configurations = configurations;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout); factory.setReadTimeout(readTimeout);
    this.http = new RestTemplate(factory);
  }

  @Override
  public JsonNode completeJson(
      long organizationId, String systemPrompt, String userPrompt, JsonNode schema) {
    return completeJson(configurations.resolve(organizationId), systemPrompt, userPrompt, schema);
  }

  @Override
  public JsonNode completeJson(
      AiConnection connection, String systemPrompt, String userPrompt, JsonNode schema) {
    if (connection == null || !connection.isConfigured()) throw new AiNotConfiguredException();
    ObjectNode request = json.createObjectNode();
    request.put("model", connection.getModel()); request.put("temperature", 0.1);
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
    headers.setBearerAuth(connection.getApiKey());
    try {
      JsonNode response = http.postForObject(
          endpoint(connection), new HttpEntity<JsonNode>(request, headers), JsonNode.class);
      JsonNode content = response == null
          ? null : response.path("choices").path(0).path("message").path("content");
      if (content == null || !content.isTextual()) {
        throw new AiServiceException(AiServiceException.Type.INCOMPATIBLE_RESPONSE);
      }
      return json.readTree(content.asText());
    } catch (HttpStatusCodeException failure) {
      throw statusFailure(failure.getRawStatusCode());
    } catch (ResourceAccessException failure) {
      AiServiceException.Type type = hasCause(failure, SocketTimeoutException.class)
          ? AiServiceException.Type.TIMEOUT : AiServiceException.Type.UNAVAILABLE;
      throw new AiServiceException(type, failure);
    } catch (AiServiceException failure) {
      throw failure;
    } catch (java.io.IOException invalid) {
      throw new AiServiceException(AiServiceException.Type.INCOMPATIBLE_RESPONSE);
    } catch (RestClientException invalid) {
      throw new AiServiceException(AiServiceException.Type.INCOMPATIBLE_RESPONSE);
    }
  }

  private ObjectNode message(String role, String content) {
    ObjectNode value = json.createObjectNode(); value.put("role", role); value.put("content", content); return value;
  }
  private AiServiceException statusFailure(int status) {
    AiServiceException.Type type;
    if (status == 401 || status == 403) type = AiServiceException.Type.AUTHENTICATION;
    else if (status == 404) type = AiServiceException.Type.MODEL_UNAVAILABLE;
    else type = AiServiceException.Type.UNAVAILABLE;
    return new AiServiceException(type);
  }

  private String endpoint(AiConnection connection) {
    String baseUrl = connection.getBaseUrl();
    String root = baseUrl.endsWith("/")
        ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    return root.endsWith("/v1") ? root + "/chat/completions" : root + "/v1/chat/completions";
  }

  private boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (type.isInstance(current)) return true;
    }
    return false;
  }
}
