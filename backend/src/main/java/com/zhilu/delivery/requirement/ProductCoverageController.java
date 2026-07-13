package com.zhilu.delivery.requirement;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products/{productId}/coverage")
public class ProductCoverageController {
  private final RequirementFeatureService features;

  public ProductCoverageController(RequirementFeatureService features) {
    this.features = features;
  }

  @GetMapping
  public Map<String, Object> coverage(
      @PathVariable long productId, @AuthenticationPrincipal CurrentUser user) {
    return features.productCoverage(user, productId);
  }
}
