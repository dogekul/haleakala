package com.zhilu.delivery.automation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AgentEvent {
  private String eventId;
  private String externalJobId;
  private String status;
  private int progress;
  private String error;
  private List<AgentArtifact> artifacts = new ArrayList<AgentArtifact>();

  public AgentEvent() {}

  public AgentEvent(String eventId, String externalJobId, String status, int progress,
      String error, List<AgentArtifact> artifacts) {
    this.eventId = eventId;
    this.externalJobId = externalJobId;
    this.status = status;
    this.progress = progress;
    this.error = error;
    this.artifacts = artifacts == null
        ? Collections.<AgentArtifact>emptyList() : artifacts;
  }

  public String getEventId() { return eventId; }
  public String getExternalJobId() { return externalJobId; }
  public String getStatus() { return status; }
  public int getProgress() { return progress; }
  public String getError() { return error; }
  public List<AgentArtifact> getArtifacts() { return artifacts; }
  public void setEventId(String eventId) { this.eventId = eventId; }
  public void setExternalJobId(String externalJobId) { this.externalJobId = externalJobId; }
  public void setStatus(String status) { this.status = status; }
  public void setProgress(int progress) { this.progress = progress; }
  public void setError(String error) { this.error = error; }
  public void setArtifacts(List<AgentArtifact> artifacts) { this.artifacts = artifacts; }
}
