package com.zhilu.delivery.project;

public enum DeliveryStage {
  START("启动"),
  REQUIREMENT("需求采集"),
  CUSTOM_DEV("二开实施"),
  GO_LIVE("上线切换"),
  TRIAL_HANDOVER("试运行与移交"),
  STANDARDIZATION("标准化评估"),
  CLOSE("项目收尾");

  private final String displayName;

  DeliveryStage(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
