package com.zhilu.delivery.automation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:agent-callback;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none", "spring.session.store-type=none",
    "delivery.agent.shared-secret=test-secret"
})
@AutoConfigureMockMvc
class AgentCallbackIT {
  @Autowired private MockMvc mvc;

  @Test
  void rejectsInvalidSignatureBeforeProcessing() throws Exception {
    String body = "{\"eventId\":\"bad\",\"externalJobId\":\"missing\",\"status\":\"RUNNING\",\"progress\":10}";
    mvc.perform(post("/api/v1/integrations/agent/events")
            .contentType("application/json").content(body)
            .header("X-Agent-Timestamp", Long.toString(Instant.now().getEpochSecond()))
            .header("X-Agent-Signature", "not-valid"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsExpiredSignedRequest() throws Exception {
    String body = "{\"eventId\":\"old\",\"externalJobId\":\"missing\",\"status\":\"RUNNING\",\"progress\":10}";
    long timestamp = Instant.now().minusSeconds(301).getEpochSecond();
    mvc.perform(post("/api/v1/integrations/agent/events")
            .contentType("application/json").content(body)
            .header("X-Agent-Timestamp", Long.toString(timestamp))
            .header("X-Agent-Signature", signature(timestamp, body)))
        .andExpect(status().isUnauthorized());
  }

  private String signature(long timestamp, String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] bytes = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
    StringBuilder result = new StringBuilder();
    for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
    return result.toString();
  }
}
