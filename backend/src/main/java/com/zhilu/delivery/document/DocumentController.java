package com.zhilu.delivery.document;

import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.iam.service.CurrentUser;
import com.zhilu.delivery.knowledge.KnowledgeService;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DocumentController {
  private final DocumentCenterService documents;
  private final DocumentExportService exports;
  private final KnowledgeService knowledge;
  private final AuditService audit;

  public DocumentController(
      DocumentCenterService documents, DocumentExportService exports,
      KnowledgeService knowledge, AuditService audit) {
    this.documents = documents;
    this.exports = exports;
    this.knowledge = knowledge;
    this.audit = audit;
  }

  @GetMapping("/api/v1/knowledge/{id}/document")
  public DocumentView knowledge(
      @PathVariable long id, @AuthenticationPrincipal CurrentUser user) {
    return documents.readKnowledge(id, user);
  }

  @PutMapping("/api/v1/knowledge/{id}/document")
  public DocumentView updateKnowledge(
      @PathVariable long id, @Valid @RequestBody SaveRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    DocumentView value = knowledge.updateDocument(
        id, user, request.title, request.markdown, request.revision);
    audit.record(user.getOrganizationId(), user.getId(), "EDIT", "KNOWLEDGE_DOCUMENT",
        String.valueOf(id), request.title + " · revision " + value.getRevision());
    return value;
  }

  @GetMapping("/api/v1/knowledge/{id}/document/export")
  public ResponseEntity<byte[]> exportKnowledge(
      @PathVariable long id, @RequestParam(defaultValue = "md") String format,
      @AuthenticationPrincipal CurrentUser user) {
    DocumentView document = documents.readKnowledge(id, user);
    audit.record(user.getOrganizationId(), user.getId(), "EXPORT", "KNOWLEDGE_DOCUMENT",
        String.valueOf(id), format);
    return download(document, format);
  }

  @GetMapping("/api/v1/projects/{projectId}/documents/{documentId}")
  public DocumentView project(
      @PathVariable long projectId, @PathVariable long documentId,
      @AuthenticationPrincipal CurrentUser user) {
    return documents.readProjectDocument(projectId, documentId, user);
  }

  @PutMapping("/api/v1/projects/{projectId}/documents/{documentId}")
  public DocumentView updateProject(
      @PathVariable long projectId, @PathVariable long documentId,
      @Valid @RequestBody SaveRequest request, @AuthenticationPrincipal CurrentUser user) {
    DocumentView value = documents.updateProjectDocument(
        projectId, documentId, request.title, request.markdown, request.revision, user);
    audit.record(user.getOrganizationId(), user.getId(), "EDIT", "PROJECT_DOCUMENT",
        String.valueOf(documentId), "project " + projectId + " · revision " + value.getRevision());
    return value;
  }

  @GetMapping("/api/v1/projects/{projectId}/documents/{documentId}/export")
  public ResponseEntity<byte[]> exportProject(
      @PathVariable long projectId, @PathVariable long documentId,
      @RequestParam(defaultValue = "md") String format,
      @AuthenticationPrincipal CurrentUser user) {
    DocumentView document = documents.readProjectDocument(projectId, documentId, user);
    audit.record(user.getOrganizationId(), user.getId(), "EXPORT", "PROJECT_DOCUMENT",
        String.valueOf(documentId), "project " + projectId + " · " + format);
    return download(document, format);
  }

  private ResponseEntity<byte[]> download(DocumentView document, String format) {
    DocumentExportService.Result result = exports.export(document, format);
    String fileName = safeName(document.getTitle()) + "." + result.getExtension();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, result.getContentType())
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"document." + result.getExtension()
                + "\"; filename*=UTF-8''" + encode(fileName))
        .body(result.getBytes());
  }

  private String safeName(String value) {
    String safe = value == null ? "document"
        : value.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", " ").trim();
    return safe.isEmpty() ? "document" : safe;
  }

  private String encode(String value) {
    try {
      return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    } catch (UnsupportedEncodingException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  public static final class SaveRequest {
    @NotBlank public String title;
    @NotNull public String markdown;
    @Min(0) public long revision;
  }
}
