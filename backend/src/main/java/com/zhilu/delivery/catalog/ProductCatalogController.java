package com.zhilu.delivery.catalog;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductCatalogController {
  private final ProductCatalogService catalog;

  public ProductCatalogController(ProductCatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  public List<Map<String, Object>> products() {
    return catalog.products();
  }

  @GetMapping("/{id}")
  public Map<String, Object> product(@PathVariable long id) {
    return catalog.product(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@Valid @RequestBody ProductRequest request) {
    return catalog.createProduct(request.ownerUserId, request.code, request.name, request.category);
  }

  @PutMapping("/{id}")
  public Map<String, Object> update(
      @PathVariable long id, @Valid @RequestBody ProductRequest request) {
    return catalog.updateProduct(id, request.ownerUserId, request.name, request.category, request.status);
  }

  @GetMapping("/{productId}/versions")
  public List<Map<String, Object>> versions(@PathVariable long productId) {
    return catalog.versions(productId);
  }

  @GetMapping("/{productId}/versions/{versionId}")
  public Map<String, Object> version(
      @PathVariable long productId, @PathVariable long versionId) {
    return catalog.version(productId, versionId);
  }

  @PostMapping("/{productId}/versions")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createVersion(
      @PathVariable long productId, @Valid @RequestBody VersionRequest request) {
    return catalog.createVersion(productId, request.versionName, request.releaseDate);
  }

  @PutMapping("/{productId}/versions/{versionId}")
  public Map<String, Object> updateVersion(
      @PathVariable long productId,
      @PathVariable long versionId,
      @Valid @RequestBody VersionRequest request) {
    return catalog.updateVersion(productId, versionId, request.releaseDate, request.status);
  }

  public static final class ProductRequest {
    public Long ownerUserId;
    @NotBlank public String code;
    @NotBlank public String name;
    public String category;
    public String status = "ACTIVE";
  }

  public static final class VersionRequest {
    @NotBlank public String versionName;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    public LocalDate releaseDate;
    public String status = "ACTIVE";
  }
}
