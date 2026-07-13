CREATE TABLE organization (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  code VARCHAR(64) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_organization_code UNIQUE (code)
);

CREATE TABLE team (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  name VARCHAR(120) NOT NULL,
  code VARCHAR(64) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_team_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_team_parent FOREIGN KEY (parent_id) REFERENCES team(id),
  CONSTRAINT uk_team_org_code UNIQUE (organization_id, code)
);

CREATE TABLE app_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  primary_team_id BIGINT NULL,
  username VARCHAR(80) NOT NULL,
  password_hash VARCHAR(100) NULL,
  display_name VARCHAR(120) NOT NULL,
  email VARCHAR(160) NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_user_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_user_team FOREIGN KEY (primary_team_id) REFERENCES team(id),
  CONSTRAINT uk_user_org_username UNIQUE (organization_id, username),
  CONSTRAINT uk_user_org_email UNIQUE (organization_id, email)
);

CREATE TABLE user_team (
  user_id BIGINT NOT NULL,
  team_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, team_id),
  CONSTRAINT fk_user_team_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_user_team_team FOREIGN KEY (team_id) REFERENCES team(id)
);

CREATE TABLE role (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(255) NULL,
  built_in BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_role_code UNIQUE (code)
);

CREATE TABLE permission (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(96) NOT NULL,
  name VARCHAR(120) NOT NULL,
  module VARCHAR(64) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_permission_code UNIQUE (code)
);

CREATE TABLE user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role(id)
);

CREATE TABLE role_permission (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES role(id),
  CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permission(id)
);

CREATE TABLE product (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner_user_id BIGINT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(160) NOT NULL,
  category VARCHAR(80) NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_product_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id),
  CONSTRAINT uk_product_code UNIQUE (code)
);

CREATE TABLE product_version (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  version_name VARCHAR(64) NOT NULL,
  release_date DATE NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_product_version_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT uk_product_version UNIQUE (product_id, version_name)
);

CREATE TABLE file_object (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  mime_type VARCHAR(160) NOT NULL,
  size_bytes BIGINT NOT NULL,
  checksum_sha256 CHAR(64) NOT NULL,
  file_version INT NOT NULL DEFAULT 1,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_file_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_file_creator FOREIGN KEY (created_by) REFERENCES app_user(id),
  CONSTRAINT uk_file_object_key UNIQUE (object_key)
);

CREATE TABLE audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  actor_user_id BIGINT NULL,
  action VARCHAR(96) NOT NULL,
  resource_type VARCHAR(80) NOT NULL,
  resource_id VARCHAR(80) NULL,
  trace_id VARCHAR(64) NOT NULL,
  details_text TEXT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_audit_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES app_user(id)
);

CREATE TABLE system_setting (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  setting_key VARCHAR(120) NOT NULL,
  setting_value TEXT NULL,
  encrypted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_setting_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT uk_setting_org_key UNIQUE (organization_id, setting_key)
);

CREATE INDEX idx_team_org ON team(organization_id);
CREATE INDEX idx_user_org_status ON app_user(organization_id, status);
CREATE INDEX idx_product_status ON product(status);
CREATE INDEX idx_file_org_created ON file_object(organization_id, created_at);
CREATE INDEX idx_audit_resource ON audit_log(resource_type, resource_id, created_at);
