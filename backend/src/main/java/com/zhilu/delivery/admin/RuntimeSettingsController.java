package com.zhilu.delivery.admin;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runtime-settings")
public class RuntimeSettingsController {
  private final SystemSettingService settings;

  public RuntimeSettingsController(SystemSettingService settings) {
    this.settings = settings;
  }

  @GetMapping
  public Map<String, Object> settings(@AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> stored = settings.get(user.getOrganizationId());
    Map<String, Object> visible = new LinkedHashMap<String, Object>();
    visible.put("platformName", stored.get("platformName"));
    visible.put("environmentLabel", stored.get("environmentLabel"));
    visible.put("timezone", stored.get("timezone"));
    return visible;
  }
}
