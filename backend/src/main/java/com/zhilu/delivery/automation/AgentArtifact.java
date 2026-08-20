package com.zhilu.delivery.automation;

public final class AgentArtifact {
  private String name;
  private String mimeType;
  private String content;
  private String artifactType;
  private String title;
  private Long projectDocumentId;
  private Long expectedRevision;

  public AgentArtifact() {}

  public AgentArtifact(String name, String mimeType, String content, String artifactType) {
    this(name, mimeType, content, artifactType, null, null);
  }

  public AgentArtifact(String name, String mimeType, String content, String artifactType,
      Long projectDocumentId, Long expectedRevision) {
    this.name = name;
    this.mimeType = mimeType;
    this.content = content;
    this.artifactType = artifactType;
    this.projectDocumentId = projectDocumentId;
    this.expectedRevision = expectedRevision;
  }

  public String getName() { return name; }
  public String getMimeType() { return mimeType; }
  public String getContent() { return content; }
  public String getArtifactType() { return artifactType; }
  public String getTitle() { return title; }
  public Long getProjectDocumentId() { return projectDocumentId; }
  public Long getExpectedRevision() { return expectedRevision; }
  public void setName(String name) { this.name = name; }
  public void setMimeType(String mimeType) { this.mimeType = mimeType; }
  public void setContent(String content) { this.content = content; }
  public void setArtifactType(String artifactType) { this.artifactType = artifactType; }
  public void setTitle(String title) { this.title = title; }
  public void setProjectDocumentId(Long projectDocumentId) {
    this.projectDocumentId = projectDocumentId;
  }
  public void setExpectedRevision(Long expectedRevision) {
    this.expectedRevision = expectedRevision;
  }
}
