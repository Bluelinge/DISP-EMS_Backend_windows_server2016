-- Soft-deleted rows remain for task-history foreign keys, but must not keep a
-- reusable workstation number occupied.
UPDATE workstation
SET station_no = 2000000000 + id
WHERE is_deleted = 1
  AND station_no < 2000000000;
