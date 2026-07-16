CREATE TABLE outline_document_link (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  business_key VARCHAR(180) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  outline_collection_id VARCHAR(64) NOT NULL,
  outline_document_id VARCHAR(64) NULL,
  outline_url_id VARCHAR(64) NULL,
  parent_link_id BIGINT NULL,
  title_cache VARCHAR(240) NOT NULL,
  summary_cache VARCHAR(1000) NULL,
  revision BIGINT NULL,
  outline_updated_at TIMESTAMP(6) NULL,
  sync_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  retry_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(1000) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_outline_business_key UNIQUE (organization_id,business_key),
  CONSTRAINT fk_outline_link_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_outline_link_parent FOREIGN KEY (parent_link_id) REFERENCES outline_document_link(id)
);

ALTER TABLE knowledge_item ADD COLUMN outline_link_id BIGINT NULL;
ALTER TABLE knowledge_item ADD CONSTRAINT fk_knowledge_outline_link
  FOREIGN KEY (outline_link_id) REFERENCES outline_document_link(id);

CREATE TABLE document_template_config (
  knowledge_item_id BIGINT NOT NULL,
  stage_code VARCHAR(32) NOT NULL,
  requirement VARCHAR(16) NOT NULL DEFAULT 'OPTIONAL',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  published_revision BIGINT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (knowledge_item_id),
  CONSTRAINT fk_document_template_knowledge
    FOREIGN KEY (knowledge_item_id) REFERENCES knowledge_item(id)
);

ALTER TABLE delivery_project
  ADD COLUMN document_space_status VARCHAR(24) NOT NULL DEFAULT 'PENDING';
ALTER TABLE delivery_project
  ADD COLUMN document_space_error VARCHAR(1000) NULL;

CREATE TABLE project_document (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  stage_code VARCHAR(32) NOT NULL,
  source_template_id BIGINT NOT NULL,
  source_template_revision BIGINT NOT NULL,
  outline_link_id BIGINT NULL,
  requirement VARCHAR(16) NOT NULL DEFAULT 'OPTIONAL',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  confirmed_revision BIGINT NULL,
  confirmed_by BIGINT NULL,
  confirmed_at TIMESTAMP(6) NULL,
  last_synced_at TIMESTAMP(6) NULL,
  last_error VARCHAR(1000) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_project_document_template UNIQUE (project_id,source_template_id),
  CONSTRAINT fk_project_document_project
    FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_project_document_template
    FOREIGN KEY (source_template_id) REFERENCES knowledge_item(id),
  CONSTRAINT fk_project_document_outline
    FOREIGN KEY (outline_link_id) REFERENCES outline_document_link(id),
  CONSTRAINT fk_project_document_confirmer
    FOREIGN KEY (confirmed_by) REFERENCES app_user(id)
);

CREATE TABLE document_job (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  job_type VARCHAR(32) NOT NULL,
  business_key VARCHAR(180) NOT NULL,
  business_id BIGINT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  last_error VARCHAR(1000) NULL,
  started_at TIMESTAMP(6) NULL,
  completed_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_document_job_business_key
    UNIQUE (organization_id,job_type,business_key),
  CONSTRAINT fk_document_job_org FOREIGN KEY (organization_id) REFERENCES organization(id)
);

CREATE INDEX idx_outline_link_document
  ON outline_document_link(organization_id,outline_document_id);
CREATE INDEX idx_outline_link_sync
  ON outline_document_link(organization_id,sync_status,updated_at);
CREATE INDEX idx_project_document_stage
  ON project_document(project_id,stage_code,requirement,status);
CREATE INDEX idx_document_job_due
  ON document_job(status,next_attempt_at,id);
