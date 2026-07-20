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
  private final CrmImplementationQueryService implementation;

  public CrmQueryController(
      OpportunityService opportunities, CrmImplementationQueryService implementation) {
    this.opportunities = opportunities;
    this.implementation = implementation;
  }

  @GetMapping("/owner-options")
  public List<Map<String, Object>> ownerOptions(@AuthenticationPrincipal CurrentUser user) {
    return opportunities.ownerOptions(user.getOrganizationId());
  }

  @GetMapping("/implementation")
  public List<Map<String, Object>> implementation(@AuthenticationPrincipal CurrentUser user) {
    return implementation.implementation(user.getOrganizationId());
  }

  @GetMapping("/implementation-cockpit")
  public Map<String, Object> cockpit(@AuthenticationPrincipal CurrentUser user) {
    return implementation.cockpit(user.getOrganizationId());
  }
}
