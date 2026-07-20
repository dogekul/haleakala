package com.zhilu.delivery.catalog;

import com.zhilu.delivery.document.DocumentExportService;
import com.zhilu.delivery.document.DocumentView;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products/{productId}")
public class ProductDocumentController {
  private final ProductDocumentService documents;
  private final DocumentExportService exports;

  public ProductDocumentController(
      ProductDocumentService documents, DocumentExportService exports) {
    this.documents = documents;
    this.exports = exports;
  }

  @GetMapping("/documents")
  public List<Map<String, Object>> tree(
      @PathVariable long productId, @AuthenticationPrincipal CurrentUser user) {
    return documents.tree(user.getOrganizationId(), productId);
  }

  @PostMapping("/documents/sync")
  public Map<String, Object> sync(
      @PathVariable long productId, @AuthenticationPrincipal CurrentUser user) {
    return documents.syncAllForProduct(user.getOrganizationId(), productId);
  }

  @PostMapping("/features/{featureId}/spec/sync")
  public DocumentView syncFeature(
      @PathVariable long productId, @PathVariable long featureId,
      @AuthenticationPrincipal CurrentUser user) {
    return documents.syncFeature(user.getOrganizationId(), productId, featureId);
  }

  @GetMapping("/features/{featureId}/spec")
  public DocumentView read(
      @PathVariable long productId, @PathVariable long featureId,
      @AuthenticationPrincipal CurrentUser user) {
    return documents.readSpec(user.getOrganizationId(), productId, featureId);
  }

  @PutMapping("/features/{featureId}/spec")
  public DocumentView save(
      @PathVariable long productId, @PathVariable long featureId,
      @Valid @RequestBody SaveRequest request, @AuthenticationPrincipal CurrentUser user) {
    return documents.saveSpec(user.getOrganizationId(), productId, featureId,
        request.title, request.markdown, request.revision.longValue());
  }

  @GetMapping("/features/{featureId}/spec/export")
  public ResponseEntity<byte[]> export(
      @PathVariable long productId, @PathVariable long featureId,
      @RequestParam(defaultValue = "md") String format,
      @AuthenticationPrincipal CurrentUser user) {
    DocumentView document = documents.readSpec(user.getOrganizationId(), productId, featureId);
    DocumentExportService.Result result = exports.export(document, format);
    String name = safeName(document.getTitle()) + "." + result.getExtension();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, result.getContentType())
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"feature-spec." + result.getExtension()
                + "\"; filename*=UTF-8''" + encode(name))
        .body(result.getBytes());
  }

  private String safeName(String value) {
    String safe = value == null ? "功能设计 Spec"
        : value.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", " ").trim();
    return safe.isEmpty() ? "功能设计 Spec" : safe;
  }

  private String encode(String value) {
    try { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
    catch (UnsupportedEncodingException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  public static final class SaveRequest {
    @NotBlank public String title;
    @NotBlank public String markdown;
    @NotNull @Min(0) public Long revision;
  }
}
