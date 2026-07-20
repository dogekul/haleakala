package com.zhilu.delivery.catalog;

import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/products")
public class ProductCatalogController {
  private final ProductCatalogService catalog;
  private final AuditService audit;

  public ProductCatalogController(ProductCatalogService catalog, AuditService audit) {
    this.catalog = catalog;
    this.audit = audit;
  }

  @GetMapping
  public List<Map<String, Object>> products(
      @RequestParam(defaultValue = "false") boolean bindable,
      @AuthenticationPrincipal CurrentUser user) {
    return catalog.products(user.getOrganizationId(), bindable);
  }

  @GetMapping("/{id}")
  public Map<String, Object> product(
      @PathVariable long id, @AuthenticationPrincipal CurrentUser user) {
    return catalog.product(user.getOrganizationId(), id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@Valid @RequestBody ProductRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> product =
        catalog.createProduct(user.getOrganizationId(), request.ownerUserId,
            request.code, request.name, request.category, request.description);
    audit(user, "CREATE", "PRODUCT", product.get("id"), request.name);
    return product;
  }

  @PutMapping("/{id}")
  public Map<String, Object> update(
      @PathVariable long id, @Valid @RequestBody ProductRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> product =
        catalog.updateProduct(user.getOrganizationId(), id, request.ownerUserId,
            request.name, request.category, request.description, request.status, request.version);
    audit(user, "UPDATE", "PRODUCT", id, request.name);
    return product;
  }

  @GetMapping("/{productId}/versions")
  public List<Map<String, Object>> versions(
      @PathVariable long productId,
      @RequestParam(defaultValue = "false") boolean bindable,
      @AuthenticationPrincipal CurrentUser user) {
    return catalog.versions(user.getOrganizationId(), productId, bindable);
  }

  @GetMapping("/{productId}/versions/{versionId}")
  public Map<String, Object> version(
      @PathVariable long productId, @PathVariable long versionId,
      @AuthenticationPrincipal CurrentUser user) {
    return catalog.version(user.getOrganizationId(), productId, versionId);
  }

  @PostMapping("/{productId}/versions")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createVersion(
      @PathVariable long productId, @Valid @RequestBody VersionRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> version =
        catalog.createVersion(
            user.getOrganizationId(), productId, request.versionName, request.releaseDate);
    audit(user, "CREATE", "PRODUCT_VERSION", version.get("id"), request.versionName);
    return version;
  }

  @PutMapping("/{productId}/versions/{versionId}")
  public Map<String, Object> updateVersion(
      @PathVariable long productId,
      @PathVariable long versionId,
      @Valid @RequestBody VersionRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> version =
        catalog.updateVersion(user.getOrganizationId(), productId, versionId,
            request.releaseDate, request.status, request.version);
    audit(user, "UPDATE", "PRODUCT_VERSION", versionId, request.versionName);
    return version;
  }

  private void audit(CurrentUser user, String action, String resourceType,
      Object resourceId, String details) {
    audit.record(user.getOrganizationId(), user.getId(), action, resourceType,
        String.valueOf(resourceId), details);
  }

  public static final class ProductRequest {
    public Long ownerUserId;
    @NotBlank public String code;
    @NotBlank public String name;
    public String category;
    public String description;
    public String status = "PLANNING";
    public long version;
  }

  public static final class VersionRequest {
    @NotBlank public String versionName;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    public LocalDate releaseDate;
    public String status = "PLANNING";
    public long version;
  }
}
