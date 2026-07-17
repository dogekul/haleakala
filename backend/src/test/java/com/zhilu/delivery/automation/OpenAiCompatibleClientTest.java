package com.zhilu.delivery.automation;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zhilu.delivery.common.api.ApiError;
import com.zhilu.delivery.common.error.GlobalExceptionHandler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class OpenAiCompatibleClientTest {
  private final ObjectMapper json = new ObjectMapper();
  private final AiConfigurationService configurations = mock(AiConfigurationService.class);
  private final AtomicReference<String> authorization = new AtomicReference<String>();
  private final AtomicReference<String> path = new AtomicReference<String>();
  private final AtomicReference<String> requestBody = new AtomicReference<String>();
  private HttpServer server;
  private OpenAiCompatibleClient client;
  private int responseStatus;
  private String responseBody;
  private long responseDelayMillis;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", this::respond);
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
    when(configurations.resolve(1L)).thenReturn(connection());
    client = new OpenAiCompatibleClient(json, configurations, 100, 100);
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void sendsOrganizationConnectionAndParsesStructuredContent() throws Exception {
    respondWith(200,
        "{\"choices\":[{\"message\":{\"content\":\"{\\\"status\\\":\\\"ok\\\"}\"}}]}");

    JsonNode result = client.completeJson(
        1L, "system", "user", json.createObjectNode().put("type", "object"));

    assertEquals("ok", result.path("status").asText());
    assertEquals("/v1/chat/completions", path.get());
    assertEquals("Bearer test-key", authorization.get());
    JsonNode request = json.readTree(requestBody.get());
    assertEquals("test-model", request.path("model").asText());
    assertEquals("json_schema", request.path("response_format").path("type").asText());
  }

  @Test
  void mapsAuthenticationFailures() {
    assertEquals(AiServiceException.Type.AUTHENTICATION, failureFor(401).getType());
    assertEquals(AiServiceException.Type.AUTHENTICATION, failureFor(403).getType());
  }

  @Test
  void mapsMissingModel() {
    assertEquals(AiServiceException.Type.MODEL_UNAVAILABLE, failureFor(404).getType());
  }

  @Test
  void mapsOtherUpstreamStatusesWithoutLeakingTheirBody() {
    respondWith(503, "{\"message\":\"secret upstream detail\"}");

    AiServiceException failure = assertThrows(AiServiceException.class, this::complete);

    assertEquals(AiServiceException.Type.UNAVAILABLE, failure.getType());
    assertFalse(failure.getMessage().contains("secret upstream detail"));
  }

  @Test
  void mapsReadTimeout() {
    respondWith(200,
        "{\"choices\":[{\"message\":{\"content\":\"{\\\"status\\\":\\\"ok\\\"}\"}}]}",
        500);

    AiServiceException failure = assertThrows(AiServiceException.class, this::complete);

    assertEquals(AiServiceException.Type.TIMEOUT, failure.getType());
  }

  @Test
  void mapsInvalidJsonContent() {
    assertEquals(AiServiceException.Type.INCOMPATIBLE_RESPONSE,
        failureForBody(200,
            "{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}").getType());
  }

  @Test
  void missingConfigurationIsExplicit() {
    when(configurations.resolve(1L)).thenReturn(
        new AiConnection(1L, "", "", "", "ENVIRONMENT"));

    assertThrows(AiNotConfiguredException.class,
        () -> client.completeJson(1L, "system", "user", json.createObjectNode()));
  }

  @Test
  void globalHandlerReturnsSafeBadGatewayResponse() {
    AiServiceException failure = new AiServiceException(
        AiServiceException.Type.AUTHENTICATION);

    ResponseEntity<ApiError> response = new GlobalExceptionHandler().handleAiService(failure);

    assertEquals(502, response.getStatusCodeValue());
    assertEquals("AI_AUTHENTICATION", response.getBody().getCode());
    assertEquals("AI 服务认证失败，请检查 API Key", response.getBody().getMessage());
  }

  private AiServiceException failureFor(int status) {
    return failureForBody(status, "{\"message\":\"upstream failure\"}");
  }

  private AiServiceException failureForBody(int status, String body) {
    respondWith(status, body);
    return assertThrows(AiServiceException.class, this::complete);
  }

  private JsonNode complete() {
    return client.completeJson(1L, "system", "user", json.createObjectNode());
  }

  private AiConnection connection() {
    return new AiConnection(1L,
        "http://127.0.0.1:" + server.getAddress().getPort(),
        "test-model", "test-key", "ORGANIZATION");
  }

  private void respondWith(int status, String body) {
    respondWith(status, body, 0);
  }

  private void respondWith(int status, String body, long delayMillis) {
    responseStatus = status;
    responseBody = body;
    responseDelayMillis = delayMillis;
  }

  private void respond(HttpExchange exchange) throws IOException {
    path.set(exchange.getRequestURI().getPath());
    authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
    requestBody.set(new String(readAll(exchange), UTF_8));
    if (responseDelayMillis > 0) {
      try {
        Thread.sleep(responseDelayMillis);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }
    byte[] body = responseBody.getBytes(UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(responseStatus, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }

  private byte[] readAll(HttpExchange exchange) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int read;
    while ((read = exchange.getRequestBody().read(buffer)) >= 0) {
      output.write(buffer, 0, read);
    }
    return output.toByteArray();
  }
}
