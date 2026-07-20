package com.zhilu.delivery.automation;

import com.fasterxml.jackson.databind.JsonNode;

public interface AiClient {
  JsonNode completeJson(
      long organizationId, String systemPrompt, String userPrompt, JsonNode schema);

  JsonNode completeJson(
      AiConnection connection, String systemPrompt, String userPrompt, JsonNode schema);
}
