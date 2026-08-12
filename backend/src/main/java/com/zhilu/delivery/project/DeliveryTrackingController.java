package com.zhilu.delivery.project;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/delivery-tracking")
public class DeliveryTrackingController {
  private final DeliveryTrackingService tracking;

  public DeliveryTrackingController(DeliveryTrackingService tracking) {
    this.tracking = tracking;
  }

  @GetMapping
  public List<Map<String, Object>> list(
      @PathVariable long projectId, @AuthenticationPrincipal CurrentUser user) {
    return tracking.list(projectId, user);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@PathVariable long projectId,
      @Valid @RequestBody Request request, @AuthenticationPrincipal CurrentUser user) {
    return tracking.create(projectId, request.command(), user);
  }

  @PutMapping("/{itemId}")
  public Map<String, Object> update(@PathVariable long projectId, @PathVariable long itemId,
      @Valid @RequestBody Request request, @AuthenticationPrincipal CurrentUser user) {
    return tracking.update(projectId, itemId, request.command(), request.version, user);
  }

  public static final class Request {
    @NotBlank @Size(max = 1000) public String originalRequest;
    @NotBlank public String classification;
    @NotBlank public String deliveryEnd;
    @NotBlank @Size(max = 240) public String featurePoint;
    @NotBlank public String complexity;
    @Size(max = 1000) public String productDependency;
    @NotBlank public String dependencyStatus = "NA";
    @Size(max = 500) public String dependencyNote;
    @Size(max = 160) public String extensionPoint;
    @NotNull @DecimalMin("0") public BigDecimal estimatedDays;
    @DecimalMin("0") public BigDecimal actualDays;
    @NotBlank public String reusableLevel = "NA";
    @NotBlank public String status = "TODO";
    public Long ownerUserId;
    @Size(max = 1000) public String notes;
    public long version;

    private DeliveryTrackingService.Command command() {
      return new DeliveryTrackingService.Command(originalRequest, classification, deliveryEnd,
          featurePoint, complexity, productDependency, dependencyStatus, dependencyNote,
          extensionPoint, estimatedDays, actualDays, reusableLevel, status, ownerUserId, notes);
    }
  }
}
