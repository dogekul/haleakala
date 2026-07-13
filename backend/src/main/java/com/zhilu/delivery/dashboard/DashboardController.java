package com.zhilu.delivery.dashboard;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
  private final DashboardQueryService dashboard;
  public DashboardController(DashboardQueryService dashboard) { this.dashboard = dashboard; }

  @GetMapping("/summary") public Map<String, Object> summary(
      @AuthenticationPrincipal CurrentUser user, @ModelAttribute DashboardFilter filter) {
    return dashboard.summary(user, filter);
  }
  @GetMapping("/projects") public List<Map<String, Object>> projects(
      @AuthenticationPrincipal CurrentUser user, @ModelAttribute DashboardFilter filter) {
    return dashboard.projects(user, filter);
  }
  @GetMapping("/risk-heatmap") public List<Map<String, Object>> risks(
      @AuthenticationPrincipal CurrentUser user) { return dashboard.riskHeatmap(user); }
  @GetMapping("/matrix") public List<Map<String, Object>> matrix(
      @AuthenticationPrincipal CurrentUser user) { return dashboard.matrix(user); }
}
