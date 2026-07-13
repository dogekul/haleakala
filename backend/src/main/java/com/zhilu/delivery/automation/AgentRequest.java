package com.zhilu.delivery.automation;

import java.util.Collections;
import java.util.Map;

public final class AgentRequest {
  private final String skill;
  private final String scenario;
  private final String callbackUrl;
  private final Map<String, Object> context;

  public AgentRequest(String skill, String scenario, String callbackUrl, Map<String, Object> context) {
    this.skill = skill;
    this.scenario = scenario;
    this.callbackUrl = callbackUrl;
    this.context = context == null ? Collections.<String, Object>emptyMap() : context;
  }

  public String getSkill() { return skill; }
  public String getScenario() { return scenario; }
  public String getCallbackUrl() { return callbackUrl; }
  public Map<String, Object> getContext() { return context; }
}
