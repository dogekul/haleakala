package com.zhilu.delivery.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class HttpAgentGateway implements AgentGateway {
  private final RestTemplate http;
  private final ObjectMapper json;
  private final String baseUrl;
  private final String secret;

  public HttpAgentGateway(ObjectMapper json,
      @Value("${delivery.agent.base-url:http://localhost:8090}") String baseUrl,
      @Value("${delivery.agent.shared-secret:change-me}") String secret,
      @Value("${delivery.agent.connect-timeout-ms:3000}") int connectTimeout,
      @Value("${delivery.agent.read-timeout-ms:10000}") int readTimeout) {
    this.json = json; this.baseUrl = stripSlash(baseUrl); this.secret = secret;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout); factory.setReadTimeout(readTimeout);
    this.http = new RestTemplate(factory);
  }

  @Override public AgentSubmission submit(String idempotencyKey, AgentRequest request) {
    return call(HttpMethod.POST, "/v1/jobs", request, AgentSubmission.class, idempotencyKey);
  }
  @Override public AgentEvent status(String externalJobId) {
    return call(HttpMethod.GET, "/v1/jobs/" + externalJobId, null, AgentEvent.class, null);
  }
  @Override public void cancel(String externalJobId) {
    call(HttpMethod.POST, "/v1/jobs/" + externalJobId + "/cancel", null, Object.class, null);
  }

  private <T> T call(HttpMethod method, String path, Object body, Class<T> type,
      String idempotencyKey) {
    RuntimeException last = null;
    long[] backoff = {1000L, 3000L, 9000L};
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        String payload = body == null ? "" : json.writeValueAsString(body);
        long timestamp = Instant.now().getEpochSecond();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Agent-Timestamp", Long.toString(timestamp));
        headers.set("X-Agent-Signature", sign(timestamp + "." + payload));
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
          headers.set("Idempotency-Key", idempotencyKey);
        }
        ResponseEntity<T> response = http.exchange(baseUrl + path, method,
            new HttpEntity<String>(payload, headers), type);
        return response.getBody();
      } catch (HttpClientErrorException business) {
        throw business;
      } catch (HttpServerErrorException retryable) {
        last = retryable;
      } catch (Exception retryable) {
        last = new IllegalStateException(retryable);
      }
      if (attempt < 2) sleep(backoff[attempt]);
    }
    throw new IllegalStateException("Agent 服务调用失败", last);
  }

  private String sign(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      StringBuilder result = new StringBuilder();
      for (byte item : mac.doFinal(value.getBytes(StandardCharsets.UTF_8)))
        result.append(String.format("%02x", item & 0xff));
      return result.toString();
    } catch (Exception error) { throw new IllegalStateException(error); }
  }
  private void sleep(long millis) {
    try { Thread.sleep(millis); }
    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
  }
  private static String stripSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
