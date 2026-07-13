package com.zhilu.delivery.automation;

public interface AgentGateway {
  AgentSubmission submit(String idempotencyKey, AgentRequest request);
  AgentEvent status(String externalJobId);
  void cancel(String externalJobId);
}
