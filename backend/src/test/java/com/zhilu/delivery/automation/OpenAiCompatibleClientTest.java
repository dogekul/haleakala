package com.zhilu.delivery.automation;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleClientTest {
  @Test
  void missingConfigurationIsExplicit() {
    OpenAiCompatibleClient client = new OpenAiCompatibleClient(
        new ObjectMapper(), "", "", "", 100, 100);
    assertThrows(AiNotConfiguredException.class,
        () -> client.completeJson("system", "user", new ObjectMapper().createObjectNode()));
  }
}
