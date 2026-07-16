ALTER TABLE document_template_config
  ADD COLUMN published_title_snapshot VARCHAR(240) NULL;
ALTER TABLE document_template_config
  ADD COLUMN published_markdown_snapshot LONGTEXT NULL;

ALTER TABLE project_document
  ADD COLUMN source_title_snapshot VARCHAR(240) NULL;
ALTER TABLE project_document
  ADD COLUMN source_markdown_snapshot LONGTEXT NULL;

ALTER TABLE document_job
  ADD COLUMN lease_token VARCHAR(64) NULL;
ALTER TABLE document_job
  ADD COLUMN lease_expires_at TIMESTAMP(6) NULL;
