CREATE TABLE sales_opportunity (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  customer_name_snapshot VARCHAR(180) NOT NULL,
  title VARCHAR(180) NOT NULL,
  note TEXT NULL,
  amount DECIMAL(18,2) NOT NULL DEFAULT 0,
  product_id BIGINT NULL,
  product_version_id BIGINT NULL,
  commercial_owner_user_id BIGINT NULL,
  solution_owner_user_id BIGINT NULL,
  project_manager_user_id BIGINT NULL,
  operation_owner_user_id BIGINT NULL,
  stage VARCHAR(24) NOT NULL DEFAULT 'LEAD',
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  project_id BIGINT NULL,
  stage_entered_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_opportunity_project UNIQUE (project_id),
  CONSTRAINT fk_opportunity_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_opportunity_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
  CONSTRAINT fk_opportunity_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT fk_opportunity_version FOREIGN KEY (product_version_id) REFERENCES product_version(id),
  CONSTRAINT fk_opportunity_commercial_owner FOREIGN KEY (commercial_owner_user_id) REFERENCES app_user(id),
  CONSTRAINT fk_opportunity_solution_owner FOREIGN KEY (solution_owner_user_id) REFERENCES app_user(id),
  CONSTRAINT fk_opportunity_project_manager FOREIGN KEY (project_manager_user_id) REFERENCES app_user(id),
  CONSTRAINT fk_opportunity_operation_owner FOREIGN KEY (operation_owner_user_id) REFERENCES app_user(id),
  CONSTRAINT fk_opportunity_project FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_opportunity_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE TABLE opportunity_activity (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  opportunity_id BIGINT NOT NULL,
  stage_code VARCHAR(24) NOT NULL,
  title VARCHAR(240) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'TODO',
  sort_order INT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  completed_at TIMESTAMP(6) NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_opportunity_activity_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_opportunity_activity_opportunity FOREIGN KEY (opportunity_id) REFERENCES sales_opportunity(id),
  CONSTRAINT fk_opportunity_activity_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE TABLE opportunity_artifact (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  opportunity_id BIGINT NOT NULL,
  stage_from VARCHAR(24) NOT NULL,
  artifact_type VARCHAR(48) NOT NULL,
  title VARCHAR(240) NOT NULL,
  content_markdown TEXT NULL,
  file_id BIGINT NULL,
  decision VARCHAR(16) NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_opportunity_artifact_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_opportunity_artifact_opportunity FOREIGN KEY (opportunity_id) REFERENCES sales_opportunity(id),
  CONSTRAINT fk_opportunity_artifact_file FOREIGN KEY (file_id) REFERENCES file_object(id),
  CONSTRAINT fk_opportunity_artifact_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE TABLE customer_operation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  customer_name_snapshot VARCHAR(180) NOT NULL,
  title VARCHAR(180) NOT NULL,
  stage VARCHAR(24) NOT NULL DEFAULT 'MAINTENANCE',
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  owner_user_id BIGINT NULL,
  project_id BIGINT NULL,
  opportunity_id BIGINT NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_operation_opportunity UNIQUE (opportunity_id),
  CONSTRAINT fk_operation_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_operation_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
  CONSTRAINT fk_operation_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id),
  CONSTRAINT fk_operation_project FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_operation_opportunity FOREIGN KEY (opportunity_id) REFERENCES sales_opportunity(id),
  CONSTRAINT fk_operation_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE INDEX idx_opportunity_org_status_stage
  ON sales_opportunity(organization_id,status,stage);
CREATE INDEX idx_opportunity_customer ON sales_opportunity(customer_id);
CREATE INDEX idx_opportunity_product ON sales_opportunity(product_id,product_version_id);
CREATE INDEX idx_opportunity_commercial_owner ON sales_opportunity(commercial_owner_user_id);
CREATE INDEX idx_opportunity_solution_owner ON sales_opportunity(solution_owner_user_id);
CREATE INDEX idx_opportunity_project_manager ON sales_opportunity(project_manager_user_id);
CREATE INDEX idx_opportunity_operation_owner ON sales_opportunity(operation_owner_user_id);
CREATE INDEX idx_opportunity_activity_stage
  ON opportunity_activity(opportunity_id,stage_code,sort_order);
CREATE INDEX idx_opportunity_artifact_stage
  ON opportunity_artifact(opportunity_id,stage_from,artifact_type);
CREATE INDEX idx_operation_org_status_stage
  ON customer_operation(organization_id,status,stage);
CREATE INDEX idx_operation_customer ON customer_operation(customer_id);
CREATE INDEX idx_operation_project ON customer_operation(project_id);
CREATE INDEX idx_operation_owner ON customer_operation(owner_user_id);

INSERT INTO permission(code,name,module)
SELECT 'crm:read','查看客户全生命周期','crm'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='crm:read');

INSERT INTO permission(code,name,module)
SELECT 'crm:write','维护客户全生命周期','crm'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='crm:write');

INSERT INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code IN ('crm:read','crm:write')
WHERE r.code IN ('ADMIN','PMO')
  AND NOT EXISTS (
    SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id
  );

INSERT INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='file:write'
WHERE r.code='PMO'
  AND NOT EXISTS (
    SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id
  );
