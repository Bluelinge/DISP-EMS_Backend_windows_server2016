CREATE TABLE task_product_code_audit (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  previous_product_code VARCHAR(128) NOT NULL,
  product_code VARCHAR(128) NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  workstation_id BIGINT UNSIGNED NOT NULL,
  changed_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_product_code_audit_task (task_id, changed_at),
  CONSTRAINT fk_product_code_audit_task FOREIGN KEY (task_id) REFERENCES assembly_task(id),
  CONSTRAINT fk_product_code_audit_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id),
  CONSTRAINT fk_product_code_audit_workstation FOREIGN KEY (workstation_id) REFERENCES workstation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
