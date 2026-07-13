package com.zhilu.delivery.dashboard;

public final class DashboardFilter {
  private String keyword;
  private String status;
  private String riskLevel;
  private Long productId;

  public String getKeyword() { return keyword; }
  public String getStatus() { return status; }
  public String getRiskLevel() { return riskLevel; }
  public Long getProductId() { return productId; }
  public void setKeyword(String keyword) { this.keyword = keyword; }
  public void setStatus(String status) { this.status = status; }
  public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
  public void setProductId(Long productId) { this.productId = productId; }
}
