package com.zhilu.delivery.automation;

public class AiServiceException extends RuntimeException {
  public enum Type {
    AUTHENTICATION,
    MODEL_UNAVAILABLE,
    INCOMPATIBLE_RESPONSE,
    TIMEOUT,
    UNAVAILABLE
  }

  private final Type type;

  public AiServiceException(Type type) {
    super(message(type));
    this.type = type;
  }

  public AiServiceException(Type type, Throwable cause) {
    super(message(type), cause);
    this.type = type;
  }

  public Type getType() {
    return type;
  }

  private static String message(Type type) {
    switch (type) {
      case AUTHENTICATION:
        return "AI 服务认证失败，请检查 API Key";
      case MODEL_UNAVAILABLE:
        return "AI 模型不可用，请检查模型名称和权限";
      case INCOMPATIBLE_RESPONSE:
        return "当前模型不兼容系统结构化输出要求";
      case TIMEOUT:
        return "AI 服务连接超时";
      case UNAVAILABLE:
      default:
        return "AI 服务暂时不可用";
    }
  }
}
