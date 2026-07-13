package com.zhilu.delivery.automation;

public final class AgentArtifact {
  private String name;
  private String mimeType;
  private String content;
  private String artifactType;

  public AgentArtifact() {}

  public AgentArtifact(String name, String mimeType, String content, String artifactType) {
    this.name = name;
    this.mimeType = mimeType;
    this.content = content;
    this.artifactType = artifactType;
  }

  public String getName() { return name; }
  public String getMimeType() { return mimeType; }
  public String getContent() { return content; }
  public String getArtifactType() { return artifactType; }
  public void setName(String name) { this.name = name; }
  public void setMimeType(String mimeType) { this.mimeType = mimeType; }
  public void setContent(String content) { this.content = content; }
  public void setArtifactType(String artifactType) { this.artifactType = artifactType; }
}
