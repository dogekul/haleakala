CREATE TABLE engineer_profile (
  user_id BIGINT NOT NULL,
  organization_id BIGINT NOT NULL,
  job_title VARCHAR(160) NULL,
  location VARCHAR(120) NULL,
  weekly_capacity_hours INT NOT NULL DEFAULT 40,
  resource_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_profile_org FOREIGN KEY (organization_id) REFERENCES organization(id)
);

CREATE TABLE skill_catalog (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  code VARCHAR(96) NOT NULL,
  name VARCHAR(160) NOT NULL,
  category VARCHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (id),
  CONSTRAINT uk_skill_code UNIQUE (organization_id, code),
  CONSTRAINT fk_skill_org FOREIGN KEY (organization_id) REFERENCES organization(id)
);

CREATE TABLE engineer_skill (
  user_id BIGINT NOT NULL,
  skill_id BIGINT NOT NULL,
  proficiency INT NOT NULL,
  certified BOOLEAN NOT NULL DEFAULT FALSE,
  experience_months INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, skill_id),
  CONSTRAINT fk_engineer_skill_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_engineer_skill_skill FOREIGN KEY (skill_id) REFERENCES skill_catalog(id)
);

CREATE TABLE resource_assignment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  assignment_role VARCHAR(160) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  allocation_percent INT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_assignment_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_assignment_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_assignment_project FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_assignment_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE INDEX idx_assignment_user_dates ON resource_assignment(user_id, start_date, end_date, status);
CREATE INDEX idx_assignment_project ON resource_assignment(project_id, status);
