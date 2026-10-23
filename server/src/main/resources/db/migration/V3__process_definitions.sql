CREATE TABLE process_definition (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  model VARCHAR(100) NOT NULL,
  title VARCHAR(255) NOT NULL,
  version_code VARCHAR(40) NOT NULL DEFAULT 'v1',
  source_workbook VARCHAR(255) NOT NULL DEFAULT '',
  source_document VARCHAR(500) NOT NULL DEFAULT '',
  steps_json MEDIUMTEXT NOT NULL,
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
  row_version INT UNSIGNED NOT NULL DEFAULT 1,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_process_model (model),
  KEY ix_process_active (is_deleted, model),
  CONSTRAINT fk_process_creator FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
