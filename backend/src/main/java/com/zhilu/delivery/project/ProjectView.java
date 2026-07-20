package com.zhilu.delivery.project;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ProjectView {
  private final long id;
  private final long organizationId;
  private final String code;
  private final String name;
  private final Long customerId;
  private final String customerName;
  private final long productId;
  private final String productName;
  private final long productVersionId;
  private final String productVersionName;
  private final long managerUserId;
  private final String managerName;
  private final String status;
  private final String currentStage;
  private final String riskLevel;
  private final String gateMode;
  private final String documentSpaceStatus;
  private final String documentSpaceError;
  private final LocalDate startDate;
  private final LocalDate plannedEndDate;
  private final long version;
  private final List<StageView> stages;
  private final List<Map<String, Object>> members;
  private final List<Map<String, Object>> risks;
  private final List<Map<String, Object>> milestones;
  private final List<Map<String, Object>> templates;
  private final List<Map<String, Object>> artifacts;
  private final List<Map<String, Object>> activities;

  public ProjectView(
      long id, long organizationId, String code, String name, Long customerId, String customerName,
      long productId, String productName, long productVersionId, String productVersionName,
      long managerUserId, String managerName, String status, String currentStage,
      String riskLevel, String gateMode, String documentSpaceStatus, String documentSpaceError,
      LocalDate startDate, LocalDate plannedEndDate, long version,
      List<StageView> stages, List<Map<String, Object>> members,
      List<Map<String, Object>> risks, List<Map<String, Object>> milestones,
      List<Map<String, Object>> templates, List<Map<String, Object>> artifacts,
      List<Map<String, Object>> activities) {
    this.id = id;
    this.organizationId = organizationId;
    this.code = code;
    this.name = name;
    this.customerId = customerId;
    this.customerName = customerName;
    this.productId = productId;
    this.productName = productName;
    this.productVersionId = productVersionId;
    this.productVersionName = productVersionName;
    this.managerUserId = managerUserId;
    this.managerName = managerName;
    this.status = status;
    this.currentStage = currentStage;
    this.riskLevel = riskLevel;
    this.gateMode = gateMode;
    this.documentSpaceStatus = documentSpaceStatus;
    this.documentSpaceError = documentSpaceError;
    this.startDate = startDate;
    this.plannedEndDate = plannedEndDate;
    this.version = version;
    this.stages = stages;
    this.members = members;
    this.risks = risks;
    this.milestones = milestones;
    this.templates = templates;
    this.artifacts = artifacts;
    this.activities = activities;
  }

  public long getId() { return id; }
  public long getOrganizationId() { return organizationId; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public Long getCustomerId() { return customerId; }
  public String getCustomerName() { return customerName; }
  public long getProductId() { return productId; }
  public String getProductName() { return productName; }
  public long getProductVersionId() { return productVersionId; }
  public String getProductVersionName() { return productVersionName; }
  public long getManagerUserId() { return managerUserId; }
  public String getManagerName() { return managerName; }
  public String getStatus() { return status; }
  public String getCurrentStage() { return currentStage; }
  public String getRiskLevel() { return riskLevel; }
  public String getGateMode() { return gateMode; }
  public String getDocumentSpaceStatus() { return documentSpaceStatus; }
  public String getDocumentSpaceError() { return documentSpaceError; }
  public LocalDate getStartDate() { return startDate; }
  public LocalDate getPlannedEndDate() { return plannedEndDate; }
  public long getVersion() { return version; }
  public List<StageView> getStages() { return stages; }
  public List<Map<String, Object>> getMembers() { return members; }
  public List<Map<String, Object>> getRisks() { return risks; }
  public List<Map<String, Object>> getMilestones() { return milestones; }
  public List<Map<String, Object>> getTemplates() { return templates; }
  public List<Map<String, Object>> getArtifacts() { return artifacts; }
  public List<Map<String, Object>> getActivities() { return activities; }
}
