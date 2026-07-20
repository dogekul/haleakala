ALTER TABLE opportunity_artifact ADD COLUMN outline_link_id BIGINT NULL;
ALTER TABLE opportunity_artifact ADD COLUMN source_template_id BIGINT NULL;
ALTER TABLE opportunity_artifact ADD COLUMN source_template_revision BIGINT NULL;

ALTER TABLE opportunity_artifact ADD CONSTRAINT fk_opportunity_artifact_outline
  FOREIGN KEY (outline_link_id) REFERENCES outline_document_link(id);
ALTER TABLE opportunity_artifact ADD CONSTRAINT fk_opportunity_artifact_template
  FOREIGN KEY (source_template_id) REFERENCES knowledge_item(id);
ALTER TABLE opportunity_artifact ADD CONSTRAINT uk_opportunity_artifact_outline
  UNIQUE (outline_link_id);
