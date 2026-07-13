package com.zhilu.delivery.automation;

public interface AgentGateway {
  AgentSubmission submit(AgentRequest request);
  AgentEvent status(String externalJobId);
  void cancel(String externalJobId);
}
