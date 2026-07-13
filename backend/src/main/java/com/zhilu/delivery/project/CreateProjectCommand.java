package com.zhilu.delivery.project;

import java.time.LocalDate;

public final class CreateProjectCommand {
  private final long organizationId;
  private final String code;
  private final String name;
  private final String customerName;
  private final long productId;
  private final long productVersionId;
  private final long managerUserId;
  private final long createdByUserId;
  private final LocalDate startDate;
  private final LocalDate plannedEndDate;
  private final String gateMode;

  public CreateProjectCommand(
      long organizationId,
      String code,
      String name,
      String customerName,
      long productId,
      long productVersionId,
      long managerUserId,
      LocalDate startDate,
      LocalDate plannedEndDate,
      String gateMode) {
    this(organizationId, code, name, customerName, productId, productVersionId, managerUserId,
        managerUserId, startDate, plannedEndDate, gateMode);
  }

  public CreateProjectCommand(
      long organizationId,
      String code,
      String name,
      String customerName,
      long productId,
      long productVersionId,
      long managerUserId,
      long createdByUserId,
      LocalDate startDate,
      LocalDate plannedEndDate,
      String gateMode) {
    this.organizationId = organizationId;
    this.code = code;
    this.name = name;
    this.customerName = customerName;
    this.productId = productId;
    this.productVersionId = productVersionId;
    this.managerUserId = managerUserId;
    this.createdByUserId = createdByUserId;
    this.startDate = startDate;
    this.plannedEndDate = plannedEndDate;
    this.gateMode = gateMode;
  }

  public long getOrganizationId() { return organizationId; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public String getCustomerName() { return customerName; }
  public long getProductId() { return productId; }
  public long getProductVersionId() { return productVersionId; }
  public long getManagerUserId() { return managerUserId; }
  public long getCreatedByUserId() { return createdByUserId; }
  public LocalDate getStartDate() { return startDate; }
  public LocalDate getPlannedEndDate() { return plannedEndDate; }
  public String getGateMode() { return gateMode; }
}
