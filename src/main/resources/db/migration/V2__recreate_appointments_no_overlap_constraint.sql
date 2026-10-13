-- Recreate overlap constraint for existing databases to ensure the status filter
-- is applied consistently (only PENDING/CONFIRMED should reserve a slot).
ALTER TABLE appointments
    DROP CONSTRAINT IF EXISTS appointments_no_overlap;

ALTER TABLE appointments
    ADD CONSTRAINT appointments_no_overlap
    EXCLUDE USING gist (
        employee_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    )
    WHERE (status IN ('PENDING', 'CONFIRMED'));
