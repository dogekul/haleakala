package com.zhilu.delivery.automation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AgentSignatureVerifier {
  private final String secret;
  private final long toleranceSeconds;

  public AgentSignatureVerifier(
      @Value("${delivery.agent.shared-secret:change-me}") String secret,
      @Value("${delivery.agent.signature-tolerance-seconds:300}") long toleranceSeconds) {
    this.secret = secret; this.toleranceSeconds = toleranceSeconds;
  }

  public boolean valid(String timestampValue, String signature, String body) {
    if (timestampValue == null || signature == null || body == null) return false;
    try {
      long timestamp = Long.parseLong(timestampValue);
      if (Math.abs(Instant.now().getEpochSecond() - timestamp) > toleranceSeconds) return false;
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      StringBuilder expected = new StringBuilder();
      for (byte item : mac.doFinal((timestampValue + "." + body).getBytes(StandardCharsets.UTF_8)))
        expected.append(String.format("%02x", item & 0xff));
      return MessageDigest.isEqual(expected.toString().getBytes(StandardCharsets.US_ASCII),
          signature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    } catch (Exception ignored) {
      return false;
    }
  }
}
