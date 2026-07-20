ALTER TABLE product_feature ADD COLUMN outline_link_id BIGINT NULL;
ALTER TABLE product_feature ADD COLUMN source_template_id BIGINT NULL;
ALTER TABLE product_feature ADD COLUMN source_template_revision BIGINT NULL;

ALTER TABLE product_feature ADD CONSTRAINT fk_product_feature_outline
  FOREIGN KEY (outline_link_id) REFERENCES outline_document_link(id);
ALTER TABLE product_feature ADD CONSTRAINT fk_product_feature_spec_template
  FOREIGN KEY (source_template_id) REFERENCES knowledge_item(id);
ALTER TABLE product_feature ADD CONSTRAINT uk_product_feature_outline UNIQUE (outline_link_id);
