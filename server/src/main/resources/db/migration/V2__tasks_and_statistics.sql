CREATE TABLE assembly_task (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  model VARCHAR(100) NOT NULL,
  product_code VARCHAR(128) NOT NULL,
  steel_no VARCHAR(128) NOT NULL,
  process_snapshot_json MEDIUMTEXT NOT NULL,
  status_code TINYINT UNSIGNED NOT NULL DEFAULT 0,
  started_by BIGINT UNSIGNED NOT NULL,
  started_at DATETIME(3) NOT NULL,
  ended_at DATETIME(3) NULL,
  duration_sec BIGINT UNSIGNED NULL,
  row_version INT UNSIGNED NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  KEY ix_task_status_started (status_code, started_at),
  KEY ix_task_model_ended (model, ended_at),
  KEY ix_task_product (product_code),
  KEY ix_task_steel (steel_no),
  CONSTRAINT fk_task_starter FOREIGN KEY (started_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE task_step_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  step_no INT UNSIGNED NOT NULL,
  station_id BIGINT UNSIGNED NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  action_code TINYINT UNSIGNED NOT NULL DEFAULT 0,
  values_json MEDIUMTEXT NOT NULL,
  occurred_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_step (task_id, step_no),
  KEY ix_step_log_station (station_id, occurred_at),
  CONSTRAINT fk_step_log_task FOREIGN KEY (task_id) REFERENCES assembly_task(id),
  CONSTRAINT fk_step_log_station FOREIGN KEY (station_id) REFERENCES workstation(id),
  CONSTRAINT fk_step_log_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE task_station_confirmation (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  station_id BIGINT UNSIGNED NOT NULL,
  operator_id BIGINT UNSIGNED NOT NULL,
  confirmation_json MEDIUMTEXT NOT NULL,
  confirmed_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_station_confirmation (task_id, station_id),
  CONSTRAINT fk_confirmation_task FOREIGN KEY (task_id) REFERENCES assembly_task(id),
  CONSTRAINT fk_confirmation_station FOREIGN KEY (station_id) REFERENCES workstation(id),
  CONSTRAINT fk_confirmation_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
