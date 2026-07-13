package com.zhilu.delivery.catalog;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products/{productId}")
public class ProductStructureController {
  private final ProductStructureService structures;

  public ProductStructureController(ProductStructureService structures) {
    this.structures = structures;
  }

  @GetMapping("/modules")
  public List<Map<String, Object>> modules(
      @PathVariable long productId, @AuthenticationPrincipal CurrentUser user) {
    return structures.modules(user.getOrganizationId(), productId);
  }

  @PostMapping("/modules")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createModule(
      @PathVariable long productId, @Valid @RequestBody ModuleRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    return structures.saveModule(user.getOrganizationId(), user.getId(), productId, null,
        request.parentId, request.ownerUserId, request.code, request.name, request.description,
        request.status, request.sortOrder, request.version);
  }

  @PutMapping("/modules/{moduleId}")
  public Map<String, Object> updateModule(
      @PathVariable long productId, @PathVariable long moduleId,
      @Valid @RequestBody ModuleRequest request, @AuthenticationPrincipal CurrentUser user) {
    return structures.saveModule(user.getOrganizationId(), user.getId(), productId, moduleId,
        request.parentId, request.ownerUserId, request.code, request.name, request.description,
        request.status, request.sortOrder, request.version);
  }

  @GetMapping("/features")
  public List<Map<String, Object>> features(
      @PathVariable long productId, @RequestParam(required = false) Long moduleId,
      @AuthenticationPrincipal CurrentUser user) {
    return structures.features(user.getOrganizationId(), productId, moduleId);
  }

  @PostMapping("/features")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createFeature(
      @PathVariable long productId, @Valid @RequestBody FeatureRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    return structures.saveFeature(user.getOrganizationId(), user.getId(), productId, null,
        request.moduleId, request.ownerUserId, request.code, request.name, request.description,
        request.status, request.version);
  }

  @PutMapping("/features/{featureId}")
  public Map<String, Object> updateFeature(
      @PathVariable long productId, @PathVariable long featureId,
      @Valid @RequestBody FeatureRequest request, @AuthenticationPrincipal CurrentUser user) {
    return structures.saveFeature(user.getOrganizationId(), user.getId(), productId, featureId,
        request.moduleId, request.ownerUserId, request.code, request.name, request.description,
        request.status, request.version);
  }

  public static final class ModuleRequest {
    public Long parentId;
    public Long ownerUserId;
    @NotBlank public String code;
    @NotBlank public String name;
    public String description;
    public String status = "PLANNING";
    public int sortOrder;
    public long version;
  }

  public static final class FeatureRequest {
    @NotNull public Long moduleId;
    public Long ownerUserId;
    @NotBlank public String code;
    @NotBlank public String name;
    public String description;
    public String status = "PLANNING";
    public long version;
  }
}
