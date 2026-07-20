CREATE TABLE product_document_node (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  node_type VARCHAR(16) NOT NULL,
  code VARCHAR(96) NOT NULL,
  title VARCHAR(240) NOT NULL,
  description VARCHAR(1000) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  outline_link_id BIGINT NULL,
  linked_feature_id BIGINT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_product_document_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT fk_product_document_parent FOREIGN KEY (parent_id) REFERENCES product_document_node(id),
  CONSTRAINT fk_product_document_outline FOREIGN KEY (outline_link_id) REFERENCES outline_document_link(id),
  CONSTRAINT fk_product_document_feature FOREIGN KEY (linked_feature_id) REFERENCES product_feature(id),
  CONSTRAINT uk_product_document_code UNIQUE (product_id,code),
  CONSTRAINT uk_product_document_outline UNIQUE (outline_link_id),
  CONSTRAINT uk_product_document_feature UNIQUE (linked_feature_id)
);

CREATE INDEX idx_product_document_tree
  ON product_document_node(product_id,parent_id,sort_order,id);
