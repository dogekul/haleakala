package com.zhilu.delivery.opportunity;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/crm")
public class CrmQueryController {
  private final OpportunityService opportunities;

  public CrmQueryController(OpportunityService opportunities) {
    this.opportunities = opportunities;
  }

  @GetMapping("/owner-options")
  public List<Map<String, Object>> ownerOptions(@AuthenticationPrincipal CurrentUser user) {
    return opportunities.ownerOptions(user.getOrganizationId());
  }
}
