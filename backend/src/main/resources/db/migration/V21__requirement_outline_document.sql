ALTER TABLE requirement_item ADD COLUMN outline_link_id BIGINT NULL;
ALTER TABLE requirement_item ADD COLUMN source_template_id BIGINT NULL;
ALTER TABLE requirement_item ADD COLUMN source_template_revision BIGINT NULL;

ALTER TABLE requirement_item ADD CONSTRAINT fk_requirement_outline_link
  FOREIGN KEY (outline_link_id) REFERENCES outline_document_link(id);
ALTER TABLE requirement_item ADD CONSTRAINT fk_requirement_source_template
  FOREIGN KEY (source_template_id) REFERENCES knowledge_item(id);

CREATE UNIQUE INDEX uk_requirement_outline_link ON requirement_item(outline_link_id);
