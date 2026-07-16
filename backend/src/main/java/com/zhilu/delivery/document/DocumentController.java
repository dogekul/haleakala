package com.zhilu.delivery.document;

import com.zhilu.delivery.iam.service.CurrentUser;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DocumentController {
  private final DocumentCenterService documents;

  public DocumentController(DocumentCenterService documents) {
    this.documents = documents;
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
    return documents.updateKnowledge(
        id, request.title, request.markdown, request.revision, user);
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
    return documents.updateProjectDocument(
        projectId, documentId, request.title, request.markdown, request.revision, user);
  }

  public static final class SaveRequest {
    @NotBlank public String title;
    @NotNull public String markdown;
    @Min(0) public long revision;
  }
}
