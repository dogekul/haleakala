package com.zhilu.delivery.project;

public final class StageView {
  private final long id;
  private final String code;
  private final String name;
  private final int order;
  private final String status;
  private final String gateStatus;
  private final String gateMessage;

  public StageView(long id, String code, String name, int order, String status,
      String gateStatus, String gateMessage) {
    this.id = id;
    this.code = code;
    this.name = name;
    this.order = order;
    this.status = status;
    this.gateStatus = gateStatus;
    this.gateMessage = gateMessage;
  }

  public long getId() { return id; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public int getOrder() { return order; }
  public String getStatus() { return status; }
  public String getGateStatus() { return gateStatus; }
  public String getGateMessage() { return gateMessage; }
}
