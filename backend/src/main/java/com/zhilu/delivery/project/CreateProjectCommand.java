package com.zhilu.delivery.project;

import java.time.LocalDate;

public final class CreateProjectCommand {
  private final long organizationId;
  private final String name;
  private final long customerId;
  private final long productId;
  private final long productVersionId;
  private final long managerUserId;
  private final long createdByUserId;
  private final LocalDate startDate;
  private final LocalDate plannedEndDate;
  private final String gateMode;

  public CreateProjectCommand(
      long organizationId,
      String name,
      long customerId,
      long productId,
      long productVersionId,
      long managerUserId,
      LocalDate startDate,
      LocalDate plannedEndDate,
      String gateMode) {
    this(organizationId, name, customerId, productId, productVersionId, managerUserId,
        managerUserId, startDate, plannedEndDate, gateMode);
  }

  public CreateProjectCommand(
      long organizationId,
      String name,
      long customerId,
      long productId,
      long productVersionId,
      long managerUserId,
      long createdByUserId,
      LocalDate startDate,
      LocalDate plannedEndDate,
      String gateMode) {
    this.organizationId = organizationId;
    this.name = name;
    this.customerId = customerId;
    this.productId = productId;
    this.productVersionId = productVersionId;
    this.managerUserId = managerUserId;
    this.createdByUserId = createdByUserId;
    this.startDate = startDate;
    this.plannedEndDate = plannedEndDate;
    this.gateMode = gateMode;
  }

  public long getOrganizationId() { return organizationId; }
  public String getName() { return name; }
  public long getCustomerId() { return customerId; }
  public long getProductId() { return productId; }
  public long getProductVersionId() { return productVersionId; }
  public long getManagerUserId() { return managerUserId; }
  public long getCreatedByUserId() { return createdByUserId; }
  public LocalDate getStartDate() { return startDate; }
  public LocalDate getPlannedEndDate() { return plannedEndDate; }
  public String getGateMode() { return gateMode; }
}
