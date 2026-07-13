ALTER TABLE custom_dev_task ADD COLUMN extension_point VARCHAR(160) NULL;

CREATE TABLE product_baseline (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_version_id BIGINT NOT NULL,
  capability_code VARCHAR(96) NOT NULL,
  capability_name VARCHAR(180) NOT NULL,
  dimension VARCHAR(24) NOT NULL,
  scope_description TEXT NOT NULL,
  configuration_options TEXT NULL,
  extension_points TEXT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  owner_user_id BIGINT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_baseline_capability UNIQUE (product_version_id, capability_code),
  CONSTRAINT fk_baseline_version FOREIGN KEY (product_version_id) REFERENCES product_version(id),
  CONSTRAINT fk_baseline_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id)
);

CREATE TABLE maturity_assessment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_version_id BIGINT NOT NULL,
  period_key VARCHAR(16) NOT NULL,
  standard_coverage INT NOT NULL,
  reuse_rate INT NOT NULL,
  documentation_score INT NOT NULL,
  extension_readiness INT NOT NULL,
  delivery_stability INT NOT NULL,
  maturity_score INT NOT NULL,
  assessed_by BIGINT NOT NULL,
  assessed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_maturity_period UNIQUE (product_version_id, period_key),
  CONSTRAINT fk_maturity_version FOREIGN KEY (product_version_id) REFERENCES product_version(id),
  CONSTRAINT fk_maturity_assessor FOREIGN KEY (assessed_by) REFERENCES app_user(id)
);

CREATE TABLE standardization_debt (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_version_id BIGINT NOT NULL,
  pattern_key VARCHAR(160) NOT NULL,
  title VARCHAR(240) NOT NULL,
  occurrence_count INT NOT NULL,
  distinct_projects INT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'CANDIDATE',
  owner_user_id BIGINT NULL,
  target_version VARCHAR(64) NULL,
  verification_note VARCHAR(1000) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_debt_pattern UNIQUE (product_version_id, pattern_key),
  CONSTRAINT fk_debt_version FOREIGN KEY (product_version_id) REFERENCES product_version(id),
  CONSTRAINT fk_debt_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id)
);

CREATE TABLE cost_attribution (
  id BIGINT NOT NULL AUTO_INCREMENT,
  custom_dev_task_id BIGINT NOT NULL,
  cost_category VARCHAR(64) NOT NULL,
  person_days DECIMAL(10,2) NOT NULL DEFAULT 0,
  amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  note VARCHAR(500) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_cost_task FOREIGN KEY (custom_dev_task_id) REFERENCES custom_dev_task(id)
);

CREATE TABLE flywheel_metric (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_version_id BIGINT NOT NULL,
  period_key VARCHAR(16) NOT NULL,
  confirmed_requirements INT NOT NULL,
  l0_count INT NOT NULL,
  l1_count INT NOT NULL,
  reuse_rate INT NOT NULL,
  debt_closed_count INT NOT NULL,
  custom_cost DECIMAL(14,2) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_flywheel_period UNIQUE (product_version_id, period_key),
  CONSTRAINT fk_flywheel_version FOREIGN KEY (product_version_id) REFERENCES product_version(id)
);

CREATE INDEX idx_baseline_version_dimension ON product_baseline(product_version_id, dimension);
CREATE INDEX idx_debt_status ON standardization_debt(status, distinct_projects);
CREATE INDEX idx_cost_task ON cost_attribution(custom_dev_task_id);
