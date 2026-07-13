package com.zhilu.delivery.automation;

import java.time.LocalDateTime;

public final class AgentJobView {
  private final long id;
  private final long projectId;
  private final String skillCode;
  private final String scenario;
  private final String status;
  private final int progress;
  private final String externalJobId;
  private final String errorMessage;
  private final LocalDateTime createdAt;
  private final LocalDateTime finishedAt;

  public AgentJobView(long id, long projectId, String skillCode, String scenario, String status,
      int progress, String externalJobId, String errorMessage, LocalDateTime createdAt,
      LocalDateTime finishedAt) {
    this.id = id; this.projectId = projectId; this.skillCode = skillCode;
    this.scenario = scenario; this.status = status; this.progress = progress;
    this.externalJobId = externalJobId; this.errorMessage = errorMessage;
    this.createdAt = createdAt; this.finishedAt = finishedAt;
  }
  public long getId() { return id; }
  public long getProjectId() { return projectId; }
  public String getSkillCode() { return skillCode; }
  public String getScenario() { return scenario; }
  public String getStatus() { return status; }
  public int getProgress() { return progress; }
  public String getExternalJobId() { return externalJobId; }
  public String getErrorMessage() { return errorMessage; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getFinishedAt() { return finishedAt; }
}
