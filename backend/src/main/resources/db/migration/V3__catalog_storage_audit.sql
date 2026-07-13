CREATE TABLE file_version (
  id BIGINT NOT NULL AUTO_INCREMENT,
  file_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  mime_type VARCHAR(160) NOT NULL,
  size_bytes BIGINT NOT NULL,
  checksum_sha256 CHAR(64) NOT NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_file_version_file FOREIGN KEY (file_id) REFERENCES file_object(id),
  CONSTRAINT fk_file_version_creator FOREIGN KEY (created_by) REFERENCES app_user(id),
  CONSTRAINT uk_file_version_no UNIQUE (file_id, version_no),
  CONSTRAINT uk_file_version_key UNIQUE (object_key)
);

CREATE INDEX idx_file_version_file ON file_version(file_id, version_no);
CREATE INDEX idx_product_version_status ON product_version(product_id, status);
CREATE INDEX idx_audit_org_created ON audit_log(organization_id, created_at);
