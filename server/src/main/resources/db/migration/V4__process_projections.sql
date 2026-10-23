ALTER TABLE process_definition
  ADD COLUMN projection_json MEDIUMTEXT NULL AFTER steps_json;
