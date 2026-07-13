package com.zhilu.delivery.automation;

import com.fasterxml.jackson.databind.JsonNode;

public interface AiClient {
  JsonNode completeJson(String systemPrompt, String userPrompt, JsonNode schema);
}
