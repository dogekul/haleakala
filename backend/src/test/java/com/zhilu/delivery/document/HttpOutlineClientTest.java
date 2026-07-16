package com.zhilu.delivery.document;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpOutlineClientTest {
  private static final String COLLECTION_ID = "a4296a54-2044-4529-ba86-d598a5322e06";
  private static final String DOCUMENT_ID = "015f5a38-a8f4-4ab1-b3c2-98cf41ad5d2a";
  private final ObjectMapper json = new ObjectMapper();
  private final Map<String, Response> responses = new ConcurrentHashMap<String, Response>();
  private final AtomicReference<String> authorization = new AtomicReference<String>();
  private final AtomicReference<String> requestBody = new AtomicReference<String>();
  private HttpServer server;
  private OutlineProperties properties;
  private HttpOutlineClient client;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/api", this::respond);
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
    properties = new OutlineProperties();
    properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
    properties.setApiToken("ol_api_test");
    properties.setCollectionId(COLLECTION_ID);
    properties.setConnectTimeout(Duration.ofMillis(200));
    properties.setReadTimeout(Duration.ofMillis(200));
    client = new HttpOutlineClient(properties, json);
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void createsReadsUpdatesListsAndExportsDocuments() throws Exception {
    responses.put("/api/documents.create", ok(document("项目文档", "# 根目录", 1)));
    OutlineDocument created = client.create(
        "项目文档", "# 根目录", COLLECTION_ID, null, true);

    assertEquals(DOCUMENT_ID, created.getId());
    assertEquals(1L, created.getRevision());
    assertEquals("Bearer ol_api_test", authorization.get());
    JsonNode createRequest = json.readTree(requestBody.get());
    assertEquals("项目文档", createRequest.path("title").asText());
    assertEquals(COLLECTION_ID, createRequest.path("collectionId").asText());
    assertTrue(createRequest.path("publish").asBoolean());

    responses.put("/api/documents.info", ok(document("项目文档", "# 根目录", 1)));
    assertEquals("# 根目录", client.info(DOCUMENT_ID).getText());

    responses.put("/api/documents.update", ok(document("新标题", "新正文", 2)));
    OutlineDocument updated = client.update(DOCUMENT_ID, "新标题", "新正文");
    assertEquals(2L, updated.getRevision());
    JsonNode updateRequest = json.readTree(requestBody.get());
    assertEquals(DOCUMENT_ID, updateRequest.path("id").asText());
    assertEquals("新正文", updateRequest.path("text").asText());

    responses.put("/api/documents.list",
        new Response(200, "{\"data\":[" + document("子文档", "正文", 3) + "]}"));
    List<OutlineDocument> children = client.children(DOCUMENT_ID);
    assertEquals(1, children.size());
    assertEquals("子文档", children.get(0).getTitle());
    JsonNode listRequest = json.readTree(requestBody.get());
    assertEquals(DOCUMENT_ID, listRequest.path("parentDocumentId").asText());
    assertEquals(100, listRequest.path("limit").asInt());
    assertEquals("published", listRequest.path("statusFilter").get(0).asText());

    responses.put("/api/documents.export", new Response(200, "{\"data\":\"# 导出正文\"}"));
    assertEquals("# 导出正文", client.exportMarkdown(DOCUMENT_ID));
  }

  @Test
  void mapsAuthenticationRateLimitAndUnavailableErrorsWithoutLeakingToken() {
    assertError(401, OutlineException.Type.AUTHENTICATION);
    assertError(429, OutlineException.Type.RATE_LIMIT);
    assertError(503, OutlineException.Type.UNAVAILABLE);
  }

  @Test
  void mapsReadTimeoutAndReportsConfigurationState() {
    responses.put("/api/documents.info",
        new Response(200, okBody(document("超时", "正文", 1)), 500));
    OutlineException failure = assertThrows(
        OutlineException.class, () -> client.info(DOCUMENT_ID));
    assertEquals(OutlineException.Type.TIMEOUT, failure.getType());
    assertFalse(failure.getMessage().contains("ol_api_test"));

    assertTrue(client.isConfigured());
    properties.setApiToken("");
    assertFalse(new HttpOutlineClient(properties, json).isConfigured());
  }

  private void assertError(int status, OutlineException.Type type) {
    responses.put("/api/documents.info",
        new Response(status, "{\"error\":\"failure\",\"message\":\"request failed\"}"));
    OutlineException failure = assertThrows(
        OutlineException.class, () -> client.info(DOCUMENT_ID));
    assertEquals(type, failure.getType());
    assertFalse(failure.getMessage().contains("ol_api_test"));
  }

  private Response ok(String documentJson) {
    return new Response(200, okBody(documentJson));
  }

  private String okBody(String documentJson) {
    return "{\"data\":" + documentJson + "}";
  }

  private String document(String title, String text, long revision) {
    return "{"
        + "\"id\":\"" + DOCUMENT_ID + "\","
        + "\"collectionId\":\"" + COLLECTION_ID + "\","
        + "\"parentDocumentId\":null,"
        + "\"title\":\"" + title + "\","
        + "\"text\":\"" + text + "\","
        + "\"url\":\"/doc/test-" + DOCUMENT_ID + "\","
        + "\"urlId\":\"test-url-id\","
        + "\"revision\":" + revision + ","
        + "\"updatedAt\":\"2026-07-16T08:00:00.000Z\""
        + "}";
  }

  private void respond(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
    requestBody.set(new String(readAll(exchange), UTF_8));
    Response response = responses.get(path);
    if (response == null) response = new Response(404, "{\"message\":\"missing test response\"}");
    if (response.delayMillis > 0) {
      try {
        Thread.sleep(response.delayMillis);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }
    byte[] body = response.body.getBytes(UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(response.status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }

  private byte[] readAll(HttpExchange exchange) throws IOException {
    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int read;
    while ((read = exchange.getRequestBody().read(buffer)) >= 0) {
      output.write(buffer, 0, read);
    }
    return output.toByteArray();
  }

  private static final class Response {
    private final int status;
    private final String body;
    private final long delayMillis;

    private Response(int status, String body) {
      this(status, body, 0);
    }

    private Response(int status, String body, long delayMillis) {
      this.status = status;
      this.body = body;
      this.delayMillis = delayMillis;
    }
  }
}
