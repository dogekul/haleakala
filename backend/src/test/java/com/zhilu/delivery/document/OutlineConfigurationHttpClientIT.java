package com.zhilu.delivery.document;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:outline-configuration-http;MODE=MySQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none",
    "delivery.settings.encryption-key=test-settings-master-key-2026",
    "delivery.outline.collection-id=",
    "delivery.outline.api-token="
})
class OutlineConfigurationHttpClientIT {
  private static final String COLLECTION_REFERENCE = "delivery-D4rIACBrmU";
  private static final String COLLECTION_ID = "11111111-1111-4111-8111-111111111111";

  @Autowired private OutlineConfigurationService configurations;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private final AtomicBoolean collectionInfoAttempted = new AtomicBoolean();
  private final AtomicReference<String> authorization = new AtomicReference<String>();
  private final AtomicReference<String> requestBody = new AtomicReference<String>();
  private HttpServer server;

  @BeforeEach
  void startServerWithEmptyConfiguration() throws IOException {
    jdbc.update("delete from system_setting");
    jdbc.update("delete from organization");
    jdbc.update("insert into organization(id,name,code) "
        + "values (9300,'首次配置组织','OUTLINE-FIRST-SETUP')");
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/api/collections.info", this::collectionInfo);
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void firstSetupValidatesRequestedCollectionThroughRealHttpClient() throws Exception {
    String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    OutlineConfigurationDraft draft = configurations.draft(
        9300, baseUrl, "http://outline.browser", "ol_api_first_setup",
        "http://outline.browser/collection/" + COLLECTION_REFERENCE + "/");
    OutlineProperties clientProperties = new OutlineProperties();
    clientProperties.setConnectTimeout(Duration.ofSeconds(1));
    clientProperties.setReadTimeout(Duration.ofSeconds(1));
    HttpOutlineClient client = new HttpOutlineClient(clientProperties, json);
    AtomicReference<OutlineCollection> verified = new AtomicReference<OutlineCollection>();
    AtomicReference<OutlineException> failure = new AtomicReference<OutlineException>();
    try {
      verified.set(client.testConnection(
          draft.getConnection(), draft.getCollectionReference()));
    } catch (OutlineException invalidDraft) {
      failure.set(invalidDraft);
    }

    assertAll(
        () -> assertEquals("", configurations.resolve(9300).getCollectionId()),
        () -> assertEquals(COLLECTION_REFERENCE, draft.getCollectionReference()),
        () -> assertEquals(COLLECTION_REFERENCE, draft.getConnection().getCollectionId()),
        () -> assertNull(failure.get(), () -> failure.get() == null ? ""
            : "unexpected Outline failure: " + failure.get().getType()),
        () -> assertTrue(collectionInfoAttempted.get(),
            "collections.info was not attempted"),
        () -> assertEquals("Bearer ol_api_first_setup", authorization.get()),
        () -> assertEquals(COLLECTION_REFERENCE, requestBody.get() == null ? null
            : json.readTree(requestBody.get()).path("id").asText()),
        () -> assertEquals(COLLECTION_ID,
            verified.get() == null ? null : verified.get().getId()),
        () -> assertEquals("智鹿交付",
            verified.get() == null ? null : verified.get().getName()));
  }

  private void collectionInfo(HttpExchange exchange) throws IOException {
    collectionInfoAttempted.set(true);
    authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
    requestBody.set(new String(readAll(exchange), UTF_8));
    byte[] body = ("{\"data\":{\"id\":\"" + COLLECTION_ID
        + "\",\"name\":\"智鹿交付\",\"urlId\":\"" + COLLECTION_REFERENCE
        + "\"}}").getBytes(UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, body.length);
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
