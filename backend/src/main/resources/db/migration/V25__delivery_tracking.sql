CREATE TABLE delivery_tracking_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  sequence_no INT NOT NULL,
  original_request VARCHAR(1000) NOT NULL,
  classification VARCHAR(24) NOT NULL,
  delivery_end VARCHAR(16) NOT NULL,
  feature_point VARCHAR(240) NOT NULL,
  complexity VARCHAR(8) NOT NULL,
  product_dependency VARCHAR(1000) NULL,
  dependency_status VARCHAR(16) NOT NULL DEFAULT 'NA',
  dependency_note VARCHAR(500) NULL,
  extension_point VARCHAR(160) NULL,
  estimated_days DECIMAL(10,2) NOT NULL DEFAULT 0,
  actual_days DECIMAL(10,2) NULL,
  reusable_level VARCHAR(16) NOT NULL DEFAULT 'NA',
  status VARCHAR(24) NOT NULL DEFAULT 'TODO',
  owner_user_id BIGINT NULL,
  notes VARCHAR(1000) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT uk_delivery_tracking_sequence UNIQUE (project_id, sequence_no),
  CONSTRAINT fk_delivery_tracking_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_delivery_tracking_project FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_delivery_tracking_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id),
  CONSTRAINT fk_delivery_tracking_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE INDEX idx_delivery_tracking_project_status
  ON delivery_tracking_item(project_id, status, classification);
