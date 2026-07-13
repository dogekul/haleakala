package com.zhilu.delivery.automation;

public class AiNotConfiguredException extends RuntimeException {
  public AiNotConfiguredException() { super("AI 服务尚未配置"); }
}
