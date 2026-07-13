ALTER TABLE product ADD COLUMN organization_id BIGINT NULL;
ALTER TABLE product ADD COLUMN description TEXT NULL;
UPDATE product SET organization_id=(SELECT MIN(id) FROM organization) WHERE organization_id IS NULL;
ALTER TABLE product MODIFY COLUMN organization_id BIGINT NOT NULL;
ALTER TABLE product DROP INDEX uk_product_code;
ALTER TABLE product ADD CONSTRAINT fk_product_organization FOREIGN KEY (organization_id) REFERENCES organization(id);
ALTER TABLE product ADD CONSTRAINT uk_product_org_code UNIQUE (organization_id,code);
UPDATE product SET status='ARCHIVED' WHERE status='DISABLED';
UPDATE product_version SET status='RELEASED' WHERE status='ACTIVE';
UPDATE product_version SET status='ARCHIVED' WHERE status='DISABLED';

CREATE TABLE product_module (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  owner_user_id BIGINT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(160) NOT NULL,
  description TEXT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PLANNING',
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_product_module_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT fk_product_module_parent FOREIGN KEY (parent_id) REFERENCES product_module(id),
  CONSTRAINT fk_product_module_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id),
  CONSTRAINT uk_product_module_code UNIQUE (product_id,code)
);

CREATE TABLE product_feature (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  module_id BIGINT NOT NULL,
  owner_user_id BIGINT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(180) NOT NULL,
  description TEXT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PLANNING',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_product_feature_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT fk_product_feature_module FOREIGN KEY (module_id) REFERENCES product_module(id),
  CONSTRAINT fk_product_feature_owner FOREIGN KEY (owner_user_id) REFERENCES app_user(id),
  CONSTRAINT uk_product_feature_code UNIQUE (product_id,code)
);

CREATE TABLE product_version_feature (
  product_version_id BIGINT NOT NULL,
  product_feature_id BIGINT NOT NULL,
  availability VARCHAR(24) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (product_version_id,product_feature_id),
  CONSTRAINT fk_version_feature_version FOREIGN KEY (product_version_id) REFERENCES product_version(id),
  CONSTRAINT fk_version_feature_feature FOREIGN KEY (product_feature_id) REFERENCES product_feature(id)
);

CREATE TABLE requirement_product_feature (
  requirement_id BIGINT NOT NULL,
  product_feature_id BIGINT NOT NULL,
  coverage_type VARCHAR(16) NOT NULL,
  source VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (requirement_id,product_feature_id),
  CONSTRAINT fk_requirement_feature_requirement FOREIGN KEY (requirement_id) REFERENCES requirement_item(id),
  CONSTRAINT fk_requirement_feature_feature FOREIGN KEY (product_feature_id) REFERENCES product_feature(id),
  CONSTRAINT fk_requirement_feature_creator FOREIGN KEY (created_by) REFERENCES app_user(id)
);

CREATE TABLE standardization_debt_requirement (
  standardization_debt_id BIGINT NOT NULL,
  requirement_id BIGINT NOT NULL,
  PRIMARY KEY (standardization_debt_id,requirement_id),
  CONSTRAINT fk_debt_requirement_debt FOREIGN KEY (standardization_debt_id) REFERENCES standardization_debt(id),
  CONSTRAINT fk_debt_requirement_requirement FOREIGN KEY (requirement_id) REFERENCES requirement_item(id)
);

ALTER TABLE standardization_debt ADD COLUMN converted_feature_id BIGINT NULL;
ALTER TABLE standardization_debt ADD CONSTRAINT fk_debt_converted_feature
  FOREIGN KEY (converted_feature_id) REFERENCES product_feature(id);

INSERT INTO permission(code,name,module)
SELECT 'product:read','查看产品中心','product'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='product:read');
INSERT INTO permission(code,name,module)
SELECT 'product:write','维护产品中心','product'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='product:write');
INSERT INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='product:read'
WHERE r.code IN ('ADMIN','PMO','DELIVERY_MANAGER','DELIVERY_ENGINEER','TECH_MANAGER','PRODUCT_MANAGER')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
INSERT INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='product:write'
WHERE r.code IN ('ADMIN','PRODUCT_MANAGER')
  AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
