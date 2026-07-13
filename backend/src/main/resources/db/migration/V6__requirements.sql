CREATE TABLE requirement_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  requirement_code VARCHAR(64) NOT NULL,
  title VARCHAR(240) NOT NULL,
  description TEXT NOT NULL,
  source VARCHAR(80) NULL,
  priority VARCHAR(16) NOT NULL DEFAULT 'P2',
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  validation_warning VARCHAR(500) NULL,
  merged_into_id BIGINT NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_requirement_code UNIQUE (organization_id, requirement_code),
  CONSTRAINT fk_requirement_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_requirement_project FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_requirement_creator FOREIGN KEY (created_by) REFERENCES app_user(id),
  CONSTRAINT fk_requirement_merged FOREIGN KEY (merged_into_id) REFERENCES requirement_item(id)
);

CREATE TABLE classification_suggestion (
  id BIGINT NOT NULL AUTO_INCREMENT,
  requirement_id BIGINT NOT NULL,
  suggested_level VARCHAR(8) NOT NULL,
  confidence DECIMAL(5,4) NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  source VARCHAR(24) NOT NULL DEFAULT 'AI',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_suggestion_requirement FOREIGN KEY (requirement_id) REFERENCES requirement_item(id)
);

CREATE TABLE classification_decision (
  id BIGINT NOT NULL AUTO_INCREMENT,
  requirement_id BIGINT NOT NULL,
  confirmed_level VARCHAR(8) NOT NULL,
  suggestion_level VARCHAR(8) NULL,
  override_reason VARCHAR(1000) NULL,
  confirmed_by BIGINT NOT NULL,
  confirmed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_decision_requirement UNIQUE (requirement_id),
  CONSTRAINT fk_decision_requirement FOREIGN KEY (requirement_id) REFERENCES requirement_item(id),
  CONSTRAINT fk_decision_user FOREIGN KEY (confirmed_by) REFERENCES app_user(id)
);

CREATE TABLE duplicate_relation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_requirement_id BIGINT NOT NULL,
  target_requirement_id BIGINT NOT NULL,
  similarity_score DECIMAL(5,4) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'SUGGESTED',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_duplicate_pair UNIQUE (source_requirement_id, target_requirement_id),
  CONSTRAINT fk_duplicate_source FOREIGN KEY (source_requirement_id) REFERENCES requirement_item(id),
  CONSTRAINT fk_duplicate_target FOREIGN KEY (target_requirement_id) REFERENCES requirement_item(id)
);

CREATE TABLE custom_dev_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  requirement_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  title VARCHAR(240) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'BACKLOG',
  technical_owner_id BIGINT NULL,
  estimated_person_days DECIMAL(10,2) NULL,
  actual_person_days DECIMAL(10,2) NULL,
  estimated_cost DECIMAL(14,2) NULL,
  actual_cost DECIMAL(14,2) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_custom_dev_requirement UNIQUE (requirement_id),
  CONSTRAINT fk_custom_dev_requirement FOREIGN KEY (requirement_id) REFERENCES requirement_item(id),
  CONSTRAINT fk_custom_dev_project FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_custom_dev_owner FOREIGN KEY (technical_owner_id) REFERENCES app_user(id)
);

CREATE INDEX idx_requirement_project_status ON requirement_item(project_id, status, priority);
CREATE INDEX idx_requirement_org_created ON requirement_item(organization_id, created_at);
CREATE INDEX idx_decision_level ON classification_decision(confirmed_level, confirmed_at);
CREATE INDEX idx_custom_dev_project_status ON custom_dev_task(project_id, status);
