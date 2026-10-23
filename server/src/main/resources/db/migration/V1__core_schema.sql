CREATE TABLE sys_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  no VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role_code TINYINT UNSIGNED NOT NULL DEFAULT 0,
  department VARCHAR(100) NOT NULL DEFAULT '',
  phone VARCHAR(40) NOT NULL DEFAULT '',
  status_code TINYINT UNSIGNED NOT NULL DEFAULT 1,
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
  row_version INT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_no (no),
  KEY ix_user_active (is_deleted, status_code, role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE workstation (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  station_no INT UNSIGNED NOT NULL,
  name VARCHAR(100) NOT NULL,
  capture_mode TINYINT UNSIGNED NOT NULL DEFAULT 1,
  is_default TINYINT UNSIGNED NOT NULL DEFAULT 0,
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
  row_version INT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_workstation_no (station_no),
  KEY ix_workstation_active (is_deleted, station_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE auth_session (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  token_hash CHAR(64) NOT NULL,
  port_code TINYINT UNSIGNED NOT NULL DEFAULT 0,
  workstation_id BIGINT UNSIGNED NULL,
  session_state TINYINT UNSIGNED NOT NULL DEFAULT 1,
  expires_at DATETIME(3) NOT NULL,
  revoked_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_token (token_hash),
  KEY ix_session_active (user_id, session_state, expires_at),
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
  CONSTRAINT fk_session_station FOREIGN KEY (workstation_id) REFERENCES workstation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE stored_file (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  original_name VARCHAR(255) NOT NULL,
  object_key VARCHAR(500) NOT NULL,
  mime_type VARCHAR(150) NOT NULL,
  byte_size BIGINT UNSIGNED NOT NULL,
  sha256 CHAR(64) NOT NULL,
  file_kind TINYINT UNSIGNED NOT NULL,
  processing_state TINYINT UNSIGNED NOT NULL DEFAULT 0,
  duration_ms BIGINT UNSIGNED NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
  row_version INT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_file_object (object_key),
  KEY ix_file_sha (sha256),
  KEY ix_file_active (is_deleted, file_kind, processing_state),
  CONSTRAINT fk_file_creator FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE video_category (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  parent_id BIGINT UNSIGNED NULL,
  level_no TINYINT UNSIGNED NOT NULL,
  sort_order INT UNSIGNED NOT NULL DEFAULT 1,
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
  row_version INT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_category_parent (parent_id, is_deleted, sort_order),
  CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES video_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE video_resource (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  normalized_name VARCHAR(255) NOT NULL,
  category_id BIGINT UNSIGNED NOT NULL,
  current_version_id BIGINT UNSIGNED NULL,
  allow_playback TINYINT UNSIGNED NOT NULL DEFAULT 1,
  batch_no VARCHAR(100) NOT NULL,
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
  row_version INT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_video_normalized (normalized_name),
  KEY ix_video_list (is_deleted, category_id, name),
  CONSTRAINT fk_video_category FOREIGN KEY (category_id) REFERENCES video_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE video_file_version (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  video_id BIGINT UNSIGNED NOT NULL,
  version_no INT UNSIGNED NOT NULL,
  file_id BIGINT UNSIGNED NOT NULL,
  batch_no VARCHAR(100) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_video_version (video_id, version_no),
  CONSTRAINT fk_video_version_video FOREIGN KEY (video_id) REFERENCES video_resource(id),
  CONSTRAINT fk_video_version_file FOREIGN KEY (file_id) REFERENCES stored_file(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

ALTER TABLE video_resource
  ADD CONSTRAINT fk_video_current_version FOREIGN KEY (current_version_id) REFERENCES video_file_version(id);

CREATE TABLE projection_icon (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  file_id BIGINT UNSIGNED NOT NULL,
  source_code TINYINT UNSIGNED NOT NULL DEFAULT 1,
  is_built_in TINYINT UNSIGNED NOT NULL DEFAULT 0,
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
  row_version INT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_icon_name (name),
  CONSTRAINT fk_icon_file FOREIGN KEY (file_id) REFERENCES stored_file(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE projection_template (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(500) NOT NULL DEFAULT '',
  is_default TINYINT UNSIGNED NOT NULL DEFAULT 0,
  width_px INT UNSIGNED NOT NULL DEFAULT 1920,
  height_px INT UNSIGNED NOT NULL DEFAULT 1080,
  elements_json MEDIUMTEXT NOT NULL,
  is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
  row_version INT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_template_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE api_idempotency (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  response_json MEDIUMTEXT NULL,
  status_code INT NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_idempotency_user_key (user_id, idempotency_key),
  CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE task_event (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  payload_json MEDIUMTEXT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_task_event (task_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
