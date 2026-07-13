CREATE TABLE knowledge_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  type VARCHAR(24) NOT NULL,
  title VARCHAR(240) NOT NULL,
  summary VARCHAR(1000) NOT NULL,
  content_text TEXT NOT NULL,
  tags_text VARCHAR(500) NULL,
  product_id BIGINT NULL,
  product_version_id BIGINT NULL,
  visibility VARCHAR(24) NOT NULL DEFAULT 'ORGANIZATION',
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  owner_user_id BIGINT NOT NULL,
  published_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_knowledge_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_knowledge_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT fk_knowledge_version FOREIGN KEY (product_version_id) REFERENCES product_version(id),
  CONSTRAINT fk_knowledge_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id)
);

CREATE TABLE code_snippet (
  knowledge_item_id BIGINT NOT NULL,
  language VARCHAR(64) NOT NULL,
  code_text TEXT NOT NULL,
  usage_notes TEXT NULL,
  PRIMARY KEY (knowledge_item_id),
  CONSTRAINT fk_snippet_knowledge FOREIGN KEY (knowledge_item_id) REFERENCES knowledge_item(id)
);

CREATE TABLE training_material (
  knowledge_item_id BIGINT NOT NULL,
  audience VARCHAR(240) NOT NULL,
  duration_minutes INT NOT NULL DEFAULT 0,
  file_object_id BIGINT NULL,
  PRIMARY KEY (knowledge_item_id),
  CONSTRAINT fk_training_knowledge FOREIGN KEY (knowledge_item_id) REFERENCES knowledge_item(id),
  CONSTRAINT fk_training_file FOREIGN KEY (file_object_id) REFERENCES file_object(id)
);

CREATE INDEX idx_knowledge_search ON knowledge_item(organization_id, status, type, updated_at);
CREATE INDEX idx_knowledge_product ON knowledge_item(product_id, product_version_id);
