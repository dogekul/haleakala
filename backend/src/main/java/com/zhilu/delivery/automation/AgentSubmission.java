package com.zhilu.delivery.automation;

public final class AgentSubmission {
  private String externalJobId;
  private String status;

  public AgentSubmission() {}
  public AgentSubmission(String externalJobId, String status) {
    this.externalJobId = externalJobId;
    this.status = status;
  }
  public String getExternalJobId() { return externalJobId; }
  public String getStatus() { return status; }
  public void setExternalJobId(String externalJobId) { this.externalJobId = externalJobId; }
  public void setStatus(String status) { this.status = status; }
}
