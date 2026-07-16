CREATE TABLE customer (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  name VARCHAR(180) NOT NULL,
  short_name VARCHAR(100) NULL,
  contact_name VARCHAR(100) NULL,
  phone VARCHAR(40) NULL,
  email VARCHAR(160) NULL,
  address VARCHAR(500) NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  remark TEXT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT uk_customer_org_name UNIQUE (organization_id,name),
  CONSTRAINT fk_customer_org FOREIGN KEY (organization_id) REFERENCES organization(id)
);

INSERT INTO customer(organization_id,name,status)
SELECT p.organization_id,TRIM(p.customer_name),'ACTIVE'
FROM delivery_project p
WHERE p.customer_name IS NOT NULL AND TRIM(p.customer_name)<>''
GROUP BY p.organization_id,TRIM(p.customer_name);

ALTER TABLE delivery_project ADD COLUMN customer_id BIGINT NULL;

UPDATE delivery_project p
SET customer_id=(
  SELECT c.id FROM customer c
  WHERE c.organization_id=p.organization_id AND c.name=TRIM(p.customer_name)
)
WHERE p.customer_name IS NOT NULL AND TRIM(p.customer_name)<>'';

ALTER TABLE delivery_project ADD CONSTRAINT fk_project_customer
  FOREIGN KEY (customer_id) REFERENCES customer(id);

CREATE INDEX idx_customer_org_status ON customer(organization_id,status,name);
CREATE INDEX idx_project_customer ON delivery_project(customer_id);

INSERT INTO permission(code,name,module)
SELECT 'customer:read','查看客户管理','customer'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='customer:read');

INSERT INTO permission(code,name,module)
SELECT 'customer:write','维护客户管理','customer'
WHERE NOT EXISTS (SELECT 1 FROM permission WHERE code='customer:write');

INSERT INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='customer:read'
WHERE r.built_in=TRUE
  AND NOT EXISTS (
    SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id
  );

INSERT INTO role_permission(role_id,permission_id)
SELECT r.id,p.id FROM role r JOIN permission p ON p.code='customer:write'
WHERE r.code IN ('ADMIN','PMO','DELIVERY_MANAGER')
  AND NOT EXISTS (
    SELECT 1 FROM role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id
  );
