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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
public class ProductDocumentController {
  private final ProductDocumentNodeService documents;
  private final DocumentExportService exports;

  public ProductDocumentController(
      ProductDocumentNodeService documents, DocumentExportService exports) {
    this.documents = documents;
    this.exports = exports;
  }

  @GetMapping("/document-nodes")
  public List<Map<String, Object>> nodes(
      @PathVariable long productId, @AuthenticationPrincipal CurrentUser user) {
    return documents.nodes(user.getOrganizationId(), productId);
  }

  @PostMapping("/document-nodes")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createNode(
      @PathVariable long productId, @Valid @RequestBody NodeRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    return documents.saveNode(user.getOrganizationId(), user.getId(), productId, null,
        request.parentId, request.nodeType, request.code, request.title, request.description,
        request.sortOrder, request.linkedFeatureId, request.version);
  }

  @PutMapping("/document-nodes/{nodeId}")
  public Map<String, Object> updateNode(
      @PathVariable long productId, @PathVariable long nodeId,
      @Valid @RequestBody NodeRequest request, @AuthenticationPrincipal CurrentUser user) {
    return documents.saveNode(user.getOrganizationId(), user.getId(), productId, nodeId,
        request.parentId, request.nodeType, request.code, request.title, request.description,
        request.sortOrder, request.linkedFeatureId, request.version);
  }

  @PostMapping("/document-nodes/{nodeId}/retry")
  public Map<String, Object> retryNode(
      @PathVariable long productId, @PathVariable long nodeId,
      @AuthenticationPrincipal CurrentUser user) {
    return documents.retry(user.getOrganizationId(), productId, nodeId);
  }

  @GetMapping("/document-nodes/{nodeId}/content")
  public DocumentView readNode(
      @PathVariable long productId, @PathVariable long nodeId,
      @AuthenticationPrincipal CurrentUser user) {
    return documents.readContent(user.getOrganizationId(), productId, nodeId);
  }

  @PutMapping("/document-nodes/{nodeId}/content")
  public DocumentView saveNode(
      @PathVariable long productId, @PathVariable long nodeId,
      @Valid @RequestBody SaveRequest request, @AuthenticationPrincipal CurrentUser user) {
    return documents.saveContent(user.getOrganizationId(), productId, nodeId,
        request.title, request.markdown, request.revision.longValue());
  }

  @GetMapping("/document-nodes/{nodeId}/export")
  public ResponseEntity<byte[]> exportNode(
      @PathVariable long productId, @PathVariable long nodeId,
      @RequestParam(defaultValue = "md") String format,
      @AuthenticationPrincipal CurrentUser user) {
    return exported(documents.readContent(user.getOrganizationId(), productId, nodeId), format);
  }

  @GetMapping("/features/{featureId}/spec")
  public DocumentView readFeatureSpec(
      @PathVariable long productId, @PathVariable long featureId,
      @AuthenticationPrincipal CurrentUser user) {
    return documents.readFeatureSpec(user.getOrganizationId(), productId, featureId);
  }

  @PutMapping("/features/{featureId}/spec")
  public DocumentView saveFeatureSpec(
      @PathVariable long productId, @PathVariable long featureId,
      @Valid @RequestBody SaveRequest request, @AuthenticationPrincipal CurrentUser user) {
    return documents.saveFeatureSpec(user.getOrganizationId(), productId, featureId,
        request.title, request.markdown, request.revision.longValue());
  }

  @GetMapping("/features/{featureId}/spec/export")
  public ResponseEntity<byte[]> exportFeatureSpec(
      @PathVariable long productId, @PathVariable long featureId,
      @RequestParam(defaultValue = "md") String format,
      @AuthenticationPrincipal CurrentUser user) {
    return exported(documents.readFeatureSpec(
        user.getOrganizationId(), productId, featureId), format);
  }

  private ResponseEntity<byte[]> exported(DocumentView document, String format) {
    DocumentExportService.Result result = exports.export(document, format);
    String name = safeName(document.getTitle()) + "." + result.getExtension();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, result.getContentType())
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"product-document." + result.getExtension()
                + "\"; filename*=UTF-8''" + encode(name))
        .body(result.getBytes());
  }

  private String safeName(String value) {
    String safe = value == null ? "产品文档"
        : value.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", " ").trim();
    return safe.isEmpty() ? "产品文档" : safe;
  }

  private String encode(String value) {
    try { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
    catch (UnsupportedEncodingException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  public static final class NodeRequest {
    public Long parentId;
    @NotBlank public String nodeType;
    @NotBlank public String code;
    @NotBlank public String title;
    public String description;
    public int sortOrder;
    public Long linkedFeatureId;
    public long version;
  }

  public static final class SaveRequest {
    @NotBlank public String title;
    @NotBlank public String markdown;
    @NotNull @Min(0) public Long revision;
  }
}
