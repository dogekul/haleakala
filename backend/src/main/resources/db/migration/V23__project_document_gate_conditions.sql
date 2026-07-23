ALTER TABLE document_template_config
  ADD COLUMN condition_code VARCHAR(32) NOT NULL DEFAULT 'ALWAYS';

ALTER TABLE project_document
  ADD COLUMN condition_code VARCHAR(32) NOT NULL DEFAULT 'ALWAYS';
