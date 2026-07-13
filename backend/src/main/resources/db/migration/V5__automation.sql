CREATE TABLE agent_job (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  skill_code VARCHAR(64) NOT NULL,
  scenario VARCHAR(24) NOT NULL DEFAULT 'normal',
  status VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
  progress INT NOT NULL DEFAULT 0,
  idempotency_key VARCHAR(96) NOT NULL,
  external_job_id VARCHAR(128) NULL,
  request_text TEXT NULL,
  error_message VARCHAR(1000) NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  started_at TIMESTAMP(6) NULL,
  finished_at TIMESTAMP(6) NULL,
  timeout_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_agent_job_idempotency UNIQUE (project_id, idempotency_key),
  CONSTRAINT uk_agent_job_external UNIQUE (external_job_id),
  CONSTRAINT fk_agent_job_project FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_agent_job_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE TABLE agent_attempt (
  id BIGINT NOT NULL AUTO_INCREMENT,
  agent_job_id BIGINT NOT NULL,
  attempt_no INT NOT NULL,
  outcome VARCHAR(24) NOT NULL,
  http_status INT NULL,
  error_message VARCHAR(1000) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_agent_attempt UNIQUE (agent_job_id, attempt_no),
  CONSTRAINT fk_agent_attempt_job FOREIGN KEY (agent_job_id) REFERENCES agent_job(id)
);

CREATE TABLE callback_receipt (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_id VARCHAR(128) NOT NULL,
  agent_job_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL,
  received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_callback_event UNIQUE (event_id),
  CONSTRAINT fk_callback_job FOREIGN KEY (agent_job_id) REFERENCES agent_job(id)
);

CREATE TABLE ai_decision (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  project_id BIGINT NULL,
  purpose VARCHAR(64) NOT NULL,
  model_name VARCHAR(120) NOT NULL,
  request_text TEXT NOT NULL,
  response_text TEXT NULL,
  status VARCHAR(24) NOT NULL,
  error_message VARCHAR(1000) NULL,
  created_by BIGINT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_ai_decision_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_ai_decision_project FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_ai_decision_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE INDEX idx_agent_job_project_created ON agent_job(project_id, created_at);
CREATE INDEX idx_agent_job_timeout ON agent_job(status, timeout_at);
CREATE INDEX idx_ai_decision_project ON ai_decision(project_id, created_at);
