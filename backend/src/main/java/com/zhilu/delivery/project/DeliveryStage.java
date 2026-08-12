package com.zhilu.delivery.project;

public enum DeliveryStage {
  START("项目立项"),
  REQUIREMENT("调研与启动"),
  CUSTOM_DEV("方案与计划"),
  GO_LIVE("开发与测试"),
  TRIAL_HANDOVER("验证与发布"),
  STANDARDIZATION("验收与结项"),
  CLOSE("过程跟进");

  private final String displayName;

  DeliveryStage(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
