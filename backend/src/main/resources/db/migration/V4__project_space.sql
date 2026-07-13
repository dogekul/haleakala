CREATE TABLE delivery_project (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(180) NOT NULL,
  customer_name VARCHAR(180) NOT NULL,
  product_id BIGINT NOT NULL,
  product_version_id BIGINT NOT NULL,
  manager_user_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  current_stage VARCHAR(32) NOT NULL DEFAULT 'START',
  risk_level VARCHAR(16) NOT NULL DEFAULT 'GREEN',
  gate_mode VARCHAR(16) NOT NULL DEFAULT 'BLOCK',
  start_date DATE NULL,
  planned_end_date DATE NULL,
  description TEXT NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_project_code UNIQUE (organization_id, code),
  CONSTRAINT fk_project_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_project_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT fk_project_product_version FOREIGN KEY (product_version_id) REFERENCES product_version(id),
  CONSTRAINT fk_project_manager FOREIGN KEY (manager_user_id) REFERENCES app_user(id),
  CONSTRAINT fk_project_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE TABLE project_member (
  project_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  project_role VARCHAR(48) NOT NULL,
  allocation_percent INT NOT NULL DEFAULT 100,
  joined_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (project_id, user_id),
  CONSTRAINT fk_project_member_project FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_project_member_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE TABLE stage_instance (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  stage_code VARCHAR(32) NOT NULL,
  stage_name VARCHAR(64) NOT NULL,
  stage_order INT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  gate_status VARCHAR(24) NOT NULL DEFAULT 'READY',
  gate_message VARCHAR(500) NULL,
  started_at TIMESTAMP(6) NULL,
  completed_at TIMESTAMP(6) NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_project_stage UNIQUE (project_id, stage_code),
  CONSTRAINT fk_stage_project FOREIGN KEY (project_id) REFERENCES delivery_project(id)
);

CREATE TABLE project_risk (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  title VARCHAR(240) NOT NULL,
  category VARCHAR(64) NOT NULL,
  probability INT NOT NULL,
  impact INT NOT NULL,
  risk_level VARCHAR(16) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
  owner_user_id BIGINT NULL,
  mitigation TEXT NULL,
  due_date DATE NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_risk_project FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_risk_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id)
);

CREATE TABLE milestone (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  name VARCHAR(180) NOT NULL,
  due_date DATE NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  progress INT NOT NULL DEFAULT 0,
  owner_user_id BIGINT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_milestone_project FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_milestone_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id)
);

CREATE TABLE template_instance (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  template_key VARCHAR(96) NOT NULL,
  title VARCHAR(180) NOT NULL,
  content_markdown TEXT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  updated_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_project_template UNIQUE (project_id, template_key),
  CONSTRAINT fk_template_project FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_template_updater FOREIGN KEY (updated_by) REFERENCES app_user(id)
);

CREATE TABLE project_artifact (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  stage_code VARCHAR(32) NULL,
  file_id BIGINT NOT NULL,
  artifact_type VARCHAR(64) NOT NULL,
  name VARCHAR(180) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_artifact_project FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_artifact_file FOREIGN KEY (file_id) REFERENCES file_object(id)
);

CREATE TABLE project_activity (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  actor_user_id BIGINT NULL,
  action VARCHAR(96) NOT NULL,
  summary VARCHAR(500) NOT NULL,
  details_text TEXT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_activity_project FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_activity_actor FOREIGN KEY (actor_user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_project_org_status ON delivery_project(organization_id, status);
CREATE INDEX idx_project_manager ON delivery_project(manager_user_id, status);
CREATE INDEX idx_project_member_user ON project_member(user_id, project_id);
CREATE INDEX idx_stage_project_order ON stage_instance(project_id, stage_order);
CREATE INDEX idx_risk_project_status ON project_risk(project_id, status, risk_level);
CREATE INDEX idx_milestone_project_due ON milestone(project_id, due_date);
CREATE INDEX idx_activity_project_created ON project_activity(project_id, created_at);
